package com.eventflow.modules.ordering.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderItem;
import com.eventflow.modules.ordering.domain.event.OrderEvents;
import com.eventflow.modules.ordering.domain.exception.EventSoldOutException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.shared.config.PlatformConfig;
import com.eventflow.shared.error.SemanticValidationException;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * S2 (parte 1): reserva inventario bajo FOR UPDATE, crea Order(PENDING) con ventana de pago
 * (defaults.order_expiration_minutes) y emite OrderCreated — todo en una TX.
 */
@Service
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final TicketingFacade ticketing;
    private final CatalogFacade catalog;
    private final OutboxPublisher outbox;
    private final PlatformConfig config;
    private final Clock clock;

    public CreateOrderUseCase(OrderRepository orderRepository, TicketingFacade ticketing,
                              CatalogFacade catalog, OutboxPublisher outbox, PlatformConfig config,
                              Clock clock) {
        this.orderRepository = orderRepository;
        this.ticketing = ticketing;
        this.catalog = catalog;
        this.outbox = outbox;
        this.config = config;
        this.clock = clock;
    }

    @Transactional
    public OrderResult execute(CreateOrderCommand cmd) {
        Instant now = clock.instant();
        List<OrderItem> items = new ArrayList<>();
        Map<UUID, String> descriptions = new HashMap<>();
        Map<UUID, CatalogFacade.EventPurchaseSnapshot> snapshotCache = new HashMap<>();

        for (CreateOrderCommand.Item requested : cmd.items()) {
            switch (requested.type()) {
                case "TICKET" -> {
                    TicketingFacade.TariffSnapshot tariff =
                            ticketing.reserve(requested.referenceId(), requested.quantity(), now);
                    CatalogFacade.EventPurchaseSnapshot event = snapshotCache.computeIfAbsent(
                            tariff.eventId(), catalog::purchaseSnapshot);
                    if (!event.isOnSale()) {
                        throw new EventSoldOutException("El evento no está a la venta (estado " + event.status() + ")");
                    }
                    OrderItem item = OrderItem.ticket(tariff.ticketTypeId(), requested.quantity(),
                            tariff.price(), tariff.name() + " — " + event.title());
                    descriptions.put(item.getId(), item.getDescription());
                    items.add(item);
                }
                case "PARKING" -> throw new SemanticValidationException("items",
                        "PARKING estará disponible en el módulo 8");
                case "EXCHANGE_TICKET" -> throw new SemanticValidationException("items",
                        "EXCHANGE_TICKET estará disponible en el módulo 6");
                default -> throw new SemanticValidationException("items",
                        "Tipo de ítem desconocido: " + requested.type());
            }
        }

        int ttlMinutes = config.intValue("defaults.order_expiration_minutes", "minutes", 15);
        Order order = Order.create(cmd.buyerId(), cmd.idempotencyKey(),
                now.plus(ttlMinutes, ChronoUnit.MINUTES), items);
        Order saved = orderRepository.save(order);

        outbox.publish("Order", saved.getId(), OrderEvents.ORDER_CREATED, OrderEvents.VERSION,
                cmd.buyerId(), Map.of(
                        "orderId", saved.getId().toString(),
                        "buyerId", cmd.buyerId().toString(),
                        "total", saved.getTotal().amount().toPlainString(),
                        "items", saved.getItems().stream().map(i -> Map.of(
                                "type", i.getItemType(),
                                "referenceId", i.getTicketTypeId().toString(),
                                "quantity", i.getQuantity())).toList()));
        return new OrderResult(saved, descriptions, Map.of(), null);
    }
}
