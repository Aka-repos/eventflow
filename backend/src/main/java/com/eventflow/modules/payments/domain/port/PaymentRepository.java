package com.eventflow.modules.payments.domain.port;

import com.eventflow.modules.payments.domain.Payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findSettledByOrderId(UUID orderId);

    boolean existsPendingForOrder(UUID orderId);

    boolean existsOpenOrSettledForOrder(UUID orderId);

    Optional<Payment> findLatestByOrderId(UUID orderId);

    /** Intents PENDING creados antes de cutoff — FOR UPDATE SKIP LOCKED (reconciliación H2). */
    List<Payment> lockStalePending(Instant cutoff, int batchSize);
}
