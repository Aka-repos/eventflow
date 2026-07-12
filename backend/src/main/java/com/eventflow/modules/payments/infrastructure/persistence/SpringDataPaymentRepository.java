package com.eventflow.modules.payments.infrastructure.persistence;

import com.eventflow.modules.payments.domain.Payment;
import com.eventflow.modules.payments.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface SpringDataPaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findFirstByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
