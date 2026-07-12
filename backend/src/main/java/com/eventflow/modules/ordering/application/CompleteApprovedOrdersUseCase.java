package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentsFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Reconciliación de órdenes (H2): una orden PENDING cuyo pago quedó APPROVED (caída entre la
 * resolución del intent y la finalización) se completa — PAID + boletos + ledger + outbox.
 * Idempotente: el finalizador ignora órdenes ya materializadas.
 */
@Service
public class CompleteApprovedOrdersUseCase {

    /** No tocar checkouts vivos: solo órdenes con al menos esta edad. */
    public static final Duration MIN_AGE = Duration.ofMinutes(1);
    private static final int BATCH_SIZE = 50;
    private static final Logger log = LoggerFactory.getLogger(CompleteApprovedOrdersUseCase.class);

    private final OrderRepository orderRepository;
    private final PaymentsFacade payments;
    private final OrderPaymentFinalizer finalizer;
    private final Clock clock;

    public CompleteApprovedOrdersUseCase(OrderRepository orderRepository, PaymentsFacade payments,
                                         OrderPaymentFinalizer finalizer, Clock clock) {
        this.orderRepository = orderRepository;
        this.payments = payments;
        this.finalizer = finalizer;
        this.clock = clock;
    }

    public int execute() {
        int completed = 0;
        for (UUID orderId : orderRepository.findPendingCreatedBefore(
                clock.instant().minus(MIN_AGE), BATCH_SIZE)) {
            var payment = payments.latestForOrder(orderId).orElse(null);
            if (payment == null || !payment.approved()) {
                continue;
            }
            finalizer.finalize(orderId, payment);
            completed++;
            log.info("order_reconciled_paid orderId={} paymentId={}", orderId, payment.id());
        }
        return completed;
    }
}
