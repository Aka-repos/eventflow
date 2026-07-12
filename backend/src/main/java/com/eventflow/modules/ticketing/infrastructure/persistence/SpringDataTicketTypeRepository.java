package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataTicketTypeRepository extends JpaRepository<TicketType, UUID> {

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);

    /** SELECT ... FOR UPDATE (S2: inventario bajo lock de fila). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM TicketType t WHERE t.id = :id")
    Optional<TicketType> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
