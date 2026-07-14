package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentResult;
import com.eventflow.modules.payments.application.PaymentsFacade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * ÚNICA superficie de ordering para otros módulos (doc 10; hoy la consume refunds, S⁶). Localiza el
 * pago de la ADQUISICIÓN del propietario actual (C2): order_item → order → pago liquidado. ordering
 * consulta a payments (S en la matriz), por eso la fachada devuelve el PaymentResult ya resuelto.
 */
@Component
public class OrderingFacade {

    private final OrderRepository orderRepository;
    private final PaymentsFacade payments;

    public OrderingFacade(OrderRepository orderRepository, PaymentsFacade payments) {
        this.orderRepository = orderRepository;
        this.payments = payments;
    }

    /** Pago APPROVED de la orden que contiene el order_item de adquisición del boleto (C2/A7). */
    @Transactional(readOnly = true)
    public Optional<PaymentResult> acquisitionPayment(UUID acquisitionOrderItemId) {
        return orderRepository.findOrderIdByItemId(acquisitionOrderItemId)
                .flatMap(payments::settledForOrder);
    }
}
