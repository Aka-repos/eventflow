package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderItem;
import com.eventflow.modules.ordering.domain.event.OrderEvents;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Liberación de inventario + evento OrderCancelled, compartido por cancel/expire/pago-rechazado. */
@Component
public class OrderingSupport {

    private final TicketingFacade ticketing;
    private final OutboxPublisher outbox;

    public OrderingSupport(TicketingFacade ticketing, OutboxPublisher outbox) {
        this.ticketing = ticketing;
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            if ("TICKET".equals(item.getItemType())) {
                ticketing.release(item.getTicketTypeId(), item.getQuantity());
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishOrderCancelled(Order order, String cause, UUID actorId) {
        outbox.publish("Order", order.getId(), OrderEvents.ORDER_CANCELLED, OrderEvents.VERSION,
                actorId, Map.of("orderId", order.getId().toString(), "cause", cause));
    }
}
