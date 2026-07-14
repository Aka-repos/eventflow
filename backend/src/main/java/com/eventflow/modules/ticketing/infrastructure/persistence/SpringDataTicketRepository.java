package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface SpringDataTicketRepository extends JpaRepository<Ticket, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT t FROM Ticket t WHERE t.id = :id")
    java.util.Optional<Ticket> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @Query("SELECT t.sourceOrderItemId, t.id FROM Ticket t WHERE t.sourceOrderItemId IN :itemIds")
    List<Object[]> ticketIdsByOrderItem(@Param("itemIds") Collection<UUID> itemIds);
}
