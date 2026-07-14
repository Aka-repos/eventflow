package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.DynamicQr;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataDynamicQrRepository extends JpaRepository<DynamicQr, UUID> {

    @Query("SELECT q FROM DynamicQr q WHERE q.ticketId = :ticketId AND q.status IN "
            + "(com.eventflow.modules.ticketing.domain.QrStatus.ACTIVE, "
            + "com.eventflow.modules.ticketing.domain.QrStatus.BLOCKED)")
    Optional<DynamicQr> findLiveByTicketId(@Param("ticketId") UUID ticketId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM DynamicQr q WHERE q.id = :id")
    Optional<DynamicQr> lockById(@Param("id") UUID id);
}
