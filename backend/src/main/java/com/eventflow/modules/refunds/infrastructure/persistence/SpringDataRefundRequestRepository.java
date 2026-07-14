package com.eventflow.modules.refunds.infrastructure.persistence;

import com.eventflow.modules.refunds.domain.RefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataRefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    boolean existsByTicketIdAndStatus(UUID ticketId,
            com.eventflow.modules.refunds.domain.RefundStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r WHERE r.id = :id")
    Optional<RefundRequest> lockById(@Param("id") UUID id);
}
