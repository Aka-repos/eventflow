package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderItem;
import com.eventflow.modules.ordering.domain.exception.OrderExpiredException;
import com.eventflow.modules.ordering.domain.exception.PaymentFailedException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentResult;
import com.eventflow.modules.payments.application.PaymentsFacade;
import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H1: cobro fuera de locks; el 402 se lanza DESPUÉS de finalizar (FAILED nunca se revierte). */
@ExtendWith(MockitoExtension.class)
class PayOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2027-01-01T12:00:00Z");

    @Mock OrderRepository orderRepository;
    @Mock PaymentsFacade payments;
    @Mock OrderPaymentFinalizer finalizer;
    @Mock OrderExpirer expirer;

    private PayOrderUseCase useCase;
    private Order order;
    private UUID buyerId;

    @BeforeEach
    void setUp() {
        useCase = new PayOrderUseCase(orderRepository, payments, finalizer, expirer,
                Clock.fixed(NOW, ZoneOffset.UTC));
        buyerId = UUID.randomUUID();
        order = Order.create(buyerId, UUID.randomUUID(), NOW.plusSeconds(600),
                List.of(OrderItem.ticket(UUID.randomUUID(), 1, Money.of("10.00", "USD"), "d")));
    }

    private PaymentResult payment(String status) {
        return new PaymentResult(UUID.randomUUID(), "FAKE", status, Money.of("10.00", "USD"),
                "DECLINED".equals(status) ? "La tarjeta fue rechazada por el emisor" : null);
    }

    @Test
    void approved_payment_finalizes_and_returns_result() {
        when(expirer.expireIfDue(buyerId, order.getId())).thenReturn(false);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        PaymentResult approved = payment("APPROVED");
        when(payments.charge(order.getId(), order.getTotal(), "FAKE")).thenReturn(approved);
        when(finalizer.finalize(order.getId(), approved))
                .thenReturn(new OrderResult(order, Map.of(), Map.of(), approved));

        OrderResult result = useCase.execute(buyerId, order.getId(), "FAKE");

        assertThat(result.payment().approved()).isTrue();
        verify(finalizer).finalize(order.getId(), approved);
    }

    @Test
    void declined_payment_throws_402_only_after_finalize() {
        when(expirer.expireIfDue(buyerId, order.getId())).thenReturn(false);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        PaymentResult declined = payment("DECLINED");
        when(payments.charge(order.getId(), order.getTotal(), "FAKE")).thenReturn(declined);
        when(finalizer.finalize(order.getId(), declined))
                .thenReturn(new OrderResult(order, Map.of(), Map.of(), declined));

        assertThatThrownBy(() -> useCase.execute(buyerId, order.getId(), "FAKE"))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("rechazada");
        verify(finalizer).finalize(order.getId(), declined);
    }

    @Test
    void expired_order_never_reaches_the_provider() {
        when(expirer.expireIfDue(buyerId, order.getId())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(buyerId, order.getId(), "FAKE"))
                .isInstanceOf(OrderExpiredException.class);
        verify(payments, never()).charge(any(), any(), any());
        verify(finalizer, never()).finalize(any(), any());
    }
}
