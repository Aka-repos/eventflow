package com.eventflow.modules.ordering.domain;

import com.eventflow.modules.ordering.domain.exception.OrderExpiredException;
import com.eventflow.modules.ordering.domain.exception.OrderNotPendingException;
import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes de la orden: total = Σ ítems (07-bd-06 §9), moneda homogénea y máquina de estados. */
class OrderTest {

    private static final Instant NOW = Instant.parse("2027-01-01T12:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(900);

    private Order pendingOrder() {
        UUID buyer = UUID.randomUUID();
        return Order.create(buyer, UUID.randomUUID(), EXPIRES, List.of(
                OrderItem.ticket(UUID.randomUUID(), 2, Money.of("45.00", "USD"), "General — Festival"),
                OrderItem.ticket(UUID.randomUUID(), 1, Money.of("120.00", "USD"), "VIP — Festival")));
    }

    @Test
    void total_is_sum_of_items() {
        Order order = pendingOrder();
        assertThat(order.getTotal()).isEqualTo(Money.of("210.00", "USD"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getId().version()).isEqualTo(7);
    }

    @Test
    void mixed_currencies_are_rejected() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), UUID.randomUUID(), EXPIRES, List.of(
                OrderItem.ticket(UUID.randomUUID(), 1, Money.of("10.00", "USD"), "A"),
                OrderItem.ticket(UUID.randomUUID(), 1, Money.of("10.00", "EUR"), "B"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_items_are_rejected() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), UUID.randomUUID(), EXPIRES, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pay_requires_pending_and_not_expired() {
        Order order = pendingOrder();
        order.ensurePayable(NOW);
        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        assertThatThrownBy(() -> order.ensurePayable(NOW)).isInstanceOf(OrderNotPendingException.class);
    }

    @Test
    void expired_pending_order_cannot_be_paid() {
        Order order = pendingOrder();
        assertThatThrownBy(() -> order.ensurePayable(EXPIRES.plusSeconds(1)))
                .isInstanceOf(OrderExpiredException.class);
    }

    @Test
    void cancel_only_from_pending() {
        Order order = pendingOrder();
        order.cancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order paid = pendingOrder();
        paid.ensurePayable(NOW);
        paid.markPaid();
        assertThatThrownBy(paid::cancel).isInstanceOf(OrderNotPendingException.class);
    }

    @Test
    void fail_only_from_pending() {
        Order order = pendingOrder();
        order.markFailed();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThatThrownBy(order::markPaid).isInstanceOf(OrderNotPendingException.class);
    }

    @Test
    void is_owned_by_buyer_only() {
        Order order = pendingOrder();
        assertThat(order.isOwnedBy(order.getBuyerId())).isTrue();
        assertThat(order.isOwnedBy(UUID.randomUUID())).isFalse();
    }
}
