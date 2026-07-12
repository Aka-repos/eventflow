package com.eventflow.modules.payments.application;

import com.eventflow.modules.payments.domain.Payment;
import com.eventflow.modules.payments.domain.port.PaymentProvider;
import com.eventflow.modules.payments.domain.port.PaymentRepository;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ÚNICA superficie de payments (doc 10). Patrón payment-intent TRIFÁSICO (H1, auditoría A2):
 *
 *   fase 1  createIntent  — TX propia + advisory lock por orden (serializa intents concurrentes);
 *                           el PENDING queda COMMITEADO antes de tocar al proveedor.
 *   fase 2  authorize     — SIN transacción: ningún lock de negocio ni conexión retenida
 *                           durante el I/O externo.
 *   fase 3  resolve       — TX propia: APPROVED/DECLINED persistido.
 *
 * Una caída entre fases deja un intent PENDING recuperable: la reconciliación (H2) consulta la
 * verdad del proveedor (lookup) y lo resuelve. uq_payments_order_settled hace el doble cobro
 * físicamente imposible (A4).
 */
@Component
public class PaymentsFacade {

    /** Un intent PENDING más viejo que esto se considera huérfano y se reconcilia. */
    public static final Duration STALE_INTENT_AFTER = Duration.ofMinutes(2);

    private final PaymentRepository paymentRepository;
    private final PaymentProvider provider;
    private final EntityManager entityManager;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public PaymentsFacade(PaymentRepository paymentRepository, PaymentProvider provider,
                          EntityManager entityManager, PlatformTransactionManager txManager, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.provider = provider;
        this.entityManager = entityManager;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Cobro completo (3 fases). MUST invocarse SIN transacción activa (H1: cero locks en fase 2). */
    public PaymentResult charge(UUID orderId, Money amount, String method) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "charge() no debe invocarse dentro de una transacción: violaría H1 (I/O bajo lock)");
        }
        UUID intentId = createIntent(orderId, amount, method);
        PaymentProvider.ProviderResult authorization = provider.authorize(intentId, orderId, amount, method);
        return resolve(intentId, authorization);
    }

    /**
     * Fase 1: reserva el intent PENDING en su propia TX. El advisory lock transaccional por orden
     * serializa la creación concurrente (el perdedor ve el PENDING del ganador ⇒ 409) sin tocar
     * filas de otros módulos y sin retener nada durante la fase 2.
     */
    public UUID createIntent(UUID orderId, Money amount, String method) {
        return requiresNew.execute(status -> {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                    .setParameter("key", "payment-intent:" + orderId)
                    .getSingleResult();
            if (paymentRepository.findSettledByOrderId(orderId).isPresent()) {
                throw new PaymentAlreadySettledException();
            }
            if (paymentRepository.existsPendingForOrder(orderId)) {
                throw new PaymentInProgressException();
            }
            return paymentRepository.save(Payment.intent(orderId, method, amount)).getId();
        });
    }

    /** Fase 3: persiste el resultado del proveedor en su propia TX. */
    public PaymentResult resolve(UUID intentId, PaymentProvider.ProviderResult authorization) {
        return requiresNew.execute(status -> {
            Payment payment = paymentRepository.findById(intentId)
                    .orElseThrow(() -> new IllegalStateException("Intent inexistente: " + intentId));
            if (authorization.approved()) {
                payment.approve(authorization.providerRef());
            } else {
                payment.decline(authorization.failureReason());
            }
            return toResult(paymentRepository.save(payment));
        });
    }

    /**
     * Reconciliación H2: resuelve intents PENDING huérfanos con la verdad del proveedor.
     * lookup vacío = el proveedor jamás lo procesó ⇒ DECLINED seguro. Idempotente (SKIP LOCKED).
     *
     * @return intents resueltos
     */
    public int reconcileStaleIntents(int batchSize) {
        List<UUID> staleIds = requiresNew.execute(status ->
                paymentRepository.lockStalePending(clock.instant().minus(STALE_INTENT_AFTER), batchSize)
                        .stream().map(Payment::getId).toList());
        int resolved = 0;
        for (UUID intentId : staleIds) {
            PaymentProvider.ProviderResult truth = provider.lookup(intentId)
                    .orElse(PaymentProvider.ProviderResult.declined(
                            "Reconciliación: el proveedor no registró este intento"));
            resolve(intentId, truth);
            resolved++;
        }
        return resolved;
    }

    /** Último pago de la orden (liquidado primero) para OrderResponse.payment. */
    @Transactional(readOnly = true)
    public Optional<PaymentResult> latestForOrder(UUID orderId) {
        return paymentRepository.findLatestByOrderId(orderId).map(PaymentsFacade::toResult);
    }

    /** Guard de expiración (ordering): una orden con intent abierto o liquidado NO debe expirar. */
    @Transactional(readOnly = true)
    public boolean hasOpenOrSettledIntent(UUID orderId) {
        return paymentRepository.existsOpenOrSettledForOrder(orderId);
    }

    private static PaymentResult toResult(Payment payment) {
        return new PaymentResult(payment.getId(), payment.getProvider(), payment.getStatus().name(),
                payment.getAmount(), payment.getFailureReason());
    }

    public static class PaymentInProgressException extends DomainException {

        public PaymentInProgressException() {
            super(ErrorCode.PAYMENT_IN_PROGRESS, "Ya hay un intento de pago en curso para esta orden");
        }
    }

    /** A4: una orden jamás se cobra dos veces. */
    public static class PaymentAlreadySettledException extends DomainException {

        public PaymentAlreadySettledException() {
            super(ErrorCode.ORDER_NOT_PENDING, "La orden ya tiene un pago liquidado");
        }
    }
}
