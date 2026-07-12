package com.eventflow.modules.payments.infrastructure.persistence;

import com.eventflow.modules.payments.application.PaymentsFacade;
import com.eventflow.modules.payments.domain.Payment;
import com.eventflow.modules.payments.domain.PaymentStatus;
import com.eventflow.modules.payments.domain.port.PaymentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaPaymentRepositoryAdapter implements PaymentRepository {

    private final SpringDataPaymentRepository jpa;
    private final EntityManager entityManager;

    JpaPaymentRepositoryAdapter(SpringDataPaymentRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    /** La carrera perdida contra uq_payments_order_settled se traduce a conflicto (api/02 §4.5). */
    @Override
    public Payment save(Payment payment) {
        try {
            return jpa.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            throw new PaymentsFacade.PaymentAlreadySettledException();
        }
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Payment> findSettledByOrderId(UUID orderId) {
        return jpa.findFirstByOrderIdAndStatusIn(orderId,
                List.of(PaymentStatus.APPROVED, PaymentStatus.REFUNDED));
    }

    @Override
    public boolean existsPendingForOrder(UUID orderId) {
        return jpa.existsByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
    }

    @Override
    public boolean existsOpenOrSettledForOrder(UUID orderId) {
        return jpa.existsByOrderIdAndStatusIn(orderId,
                List.of(PaymentStatus.PENDING, PaymentStatus.APPROVED, PaymentStatus.REFUNDED));
    }

    @Override
    public Optional<Payment> findLatestByOrderId(UUID orderId) {
        Optional<Payment> settled = findSettledByOrderId(orderId);
        return settled.isPresent() ? settled : jpa.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    }

    /** Huérfanos PENDING — FOR UPDATE SKIP LOCKED: lotes de reconciliación concurrentes sin pisarse. */
    @Override
    public List<Payment> lockStalePending(Instant cutoff, int batchSize) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery("""
                        SELECT id FROM commerce.payments
                        WHERE status = 'PENDING' AND created_at < :cutoff
                        ORDER BY created_at
                        LIMIT :batch
                        FOR UPDATE SKIP LOCKED
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("batch", batchSize)
                .getResultList();
        return ids.stream().map(id -> entityManager.find(Payment.class, id)).toList();
    }
}
