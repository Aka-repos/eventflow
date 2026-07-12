package com.eventflow.modules.ordering.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ledger.application.LedgerFacade;
import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderItem;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.modules.ordering.domain.event.OrderEvents;
import com.eventflow.modules.ordering.domain.exception.OrderNotFoundException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentResult;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TX-B del pago (H1): con el resultado del proveedor YA persistido, materializa el destino de la
 * orden bajo su lock — APPROVED ⇒ PAID + emisión con snapshot + ledger + outbox; DECLINED ⇒
 * FAILED + liberación + outbox. Idempotente: una orden ya no-PENDING se devuelve tal cual
 * (lo usa también la reconciliación de órdenes, H2).
 */
@Component
public class OrderPaymentFinalizer {

    private final OrderRepository orderRepository;
    private final TicketingFacade ticketing;
    private final CatalogFacade catalog;
    private final LedgerFacade ledger;
    private final OrderingSupport support;
    private final OrderQueryAssembler assembler;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public OrderPaymentFinalizer(OrderRepository orderRepository, TicketingFacade ticketing,
                                 CatalogFacade catalog, LedgerFacade ledger, OrderingSupport support,
                                 OrderQueryAssembler assembler, OutboxPublisher outbox, Clock clock) {
        this.orderRepository = orderRepository;
        this.ticketing = ticketing;
        this.catalog = catalog;
        this.ledger = ledger;
        this.support = support;
        this.assembler = assembler;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public OrderResult finalize(UUID orderId, PaymentResult payment) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.PENDING) {
            // ya materializada (reconciliación o request concurrente) — idempotente
            return new OrderResult(order, assembler.descriptions(order),
                    order.getStatus() == OrderStatus.PAID ? assembler.ticketIds(order) : Map.of(), payment);
        }
        if (payment.approved()) {
            order.markPaid();
            Map<UUID, List<UUID>> ticketsByItem = issueTickets(order);
            recordLedger(order);
            outbox.publish("Order", order.getId(), OrderEvents.PAYMENT_CONFIRMED, OrderEvents.VERSION,
                    order.getBuyerId(), Map.of(
                            "orderId", order.getId().toString(),
                            "paymentId", payment.id().toString(),
                            "provider", payment.provider(),
                            "amount", payment.amount().amount().toPlainString()));
            orderRepository.save(order);
            return new OrderResult(order, assembler.descriptions(order), ticketsByItem, payment);
        }
        order.markFailed();
        support.releaseInventory(order);
        outbox.publish("Order", order.getId(), OrderEvents.PAYMENT_FAILED, OrderEvents.VERSION,
                order.getBuyerId(), Map.of(
                        "orderId", order.getId().toString(),
                        "paymentId", payment.id().toString(),
                        "reason", payment.failureReason() == null ? "" : payment.failureReason()));
        orderRepository.save(order);
        return new OrderResult(order, assembler.descriptions(order), Map.of(), payment);
    }

    private Map<UUID, List<UUID>> issueTickets(Order order) {
        Instant now = clock.instant();
        Map<UUID, List<UUID>> ticketsByItem = new HashMap<>();
        Map<UUID, CatalogFacade.EventPurchaseSnapshot> snapshots = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            if (!"TICKET".equals(item.getItemType())) {
                continue;
            }
            UUID eventId = ticketing.tariffSnapshot(item.getTicketTypeId()).eventId();
            CatalogFacade.EventPurchaseSnapshot snapshot =
                    snapshots.computeIfAbsent(eventId, catalog::purchaseSnapshot);
            ticketsByItem.put(item.getId(), ticketing.issuePrimaryTickets(
                    new TicketingFacade.IssueTicketsCommand(item.getId(), item.getTicketTypeId(), eventId,
                            order.getBuyerId(), item.getQuantity(), item.getUnitPrice(),
                            snapshot.policySnapshot(), now)));
        }
        return ticketsByItem;
    }

    private void recordLedger(Order order) {
        Map<UUID, CatalogFacade.EventPurchaseSnapshot> snapshots = new HashMap<>();
        Map<UUID, Money> totalsByEvent = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            if (!"TICKET".equals(item.getItemType())) {
                continue;
            }
            UUID eventId = ticketing.tariffSnapshot(item.getTicketTypeId()).eventId();
            snapshots.computeIfAbsent(eventId, catalog::purchaseSnapshot);
            totalsByEvent.merge(eventId, item.lineTotal(), Money::add);
        }
        totalsByEvent.forEach((eventId, amount) -> ledger.recordPrimarySale(order.getBuyerId(),
                snapshots.get(eventId).organizerId(), amount, order.getId(), eventId,
                Map.of("orderId", order.getId().toString())));
    }
}
