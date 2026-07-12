package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.exception.OrderExpiredException;
import com.eventflow.modules.ordering.domain.exception.OrderNotFoundException;
import com.eventflow.modules.ordering.domain.exception.PaymentFailedException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentResult;
import com.eventflow.modules.payments.application.PaymentsFacade;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Pago en 3 pasos (H1): preflight de solo lectura (fail-fast, SIN lock) → cobro trifásico
 * (PaymentsFacade, sin TX activa) → finalización bajo lock (TX-B). El 402 se lanza al final:
 * el FAILED + liberación ya están commiteados y jamás se revierten.
 */
@Service
public class PayOrderUseCase {

    private final OrderRepository orderRepository;
    private final PaymentsFacade payments;
    private final OrderPaymentFinalizer finalizer;
    private final OrderExpirer expirer;
    private final Clock clock;

    public PayOrderUseCase(OrderRepository orderRepository, PaymentsFacade payments,
                           OrderPaymentFinalizer finalizer, OrderExpirer expirer, Clock clock) {
        this.orderRepository = orderRepository;
        this.payments = payments;
        this.finalizer = finalizer;
        this.expirer = expirer;
        this.clock = clock;
    }

    public OrderResult execute(UUID buyerId, UUID orderId, String method) {
        if (expirer.expireIfDue(buyerId, orderId)) {
            // la expiración quedó COMMITEADA en su propia TX; este 409 no la revierte
            throw new OrderExpiredException();
        }
        preflight(buyerId, orderId);
        PaymentResult payment = payments.charge(orderId, orderTotal(orderId), method);
        OrderResult result = finalizer.finalize(orderId, payment);
        if (payment.declined()) {
            throw new PaymentFailedException(payment.failureReason());
        }
        return result;
    }

    /** Solo lectura, sin lock ni TX propia: propiedad (404 anti-enumeración), estado y ventana. */
    private void preflight(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.isOwnedBy(buyerId))
                .orElseThrow(OrderNotFoundException::new);
        order.ensurePayable(clock.instant());
    }

    private com.eventflow.shared.domain.Money orderTotal(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(OrderNotFoundException::new).getTotal();
    }
}
