package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentsFacade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Materializa la expiración de UNA orden en su PROPIA transacción: el 409 order_expired que
 * el llamador lanza después jamás revierte la cancelación ni la liberación de inventario
 * (mismo patrón REQUIRES-OWN-TX que la revocación de familia de refresh tokens, módulo 1).
 */
@Component
public class OrderExpirer {

    private final OrderRepository orderRepository;
    private final OrderingSupport support;
    private final PaymentsFacade payments;
    private final Clock clock;

    public OrderExpirer(OrderRepository orderRepository, OrderingSupport support,
                        PaymentsFacade payments, Clock clock) {
        this.orderRepository = orderRepository;
        this.support = support;
        this.payments = payments;
        this.clock = clock;
    }

    /** @return true si la orden estaba vencida y quedó CANCELLED (commiteado). */
    @Transactional
    public boolean expireIfDue(UUID buyerId, UUID orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.isOwnedBy(buyerId))
                .filter(o -> o.isExpired(clock.instant()))
                // un intent abierto/liquidado bloquea la expiración: la reconciliación decide (H1/H2)
                .filter(o -> !payments.hasOpenOrSettledIntent(o.getId()))
                .map(this::expire)
                .orElse(false);
    }

    private boolean expire(Order order) {
        order.cancel();
        support.releaseInventory(order);
        support.publishOrderCancelled(order, "EXPIRED", order.getBuyerId());
        orderRepository.save(order);
        return true;
    }
}
