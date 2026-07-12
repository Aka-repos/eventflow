package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;
import com.eventflow.modules.ticketing.domain.port.TicketHistoryRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Append-only (REVOKE UPDATE/DELETE en V8). */
@Component
class JpaTicketHistoryRepositoryAdapter implements TicketHistoryRepository {

    private final EntityManager entityManager;

    JpaTicketHistoryRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(TicketHistoryEntry entry) {
        entityManager.persist(entry);
    }

    @Override
    public List<TicketHistoryEntry> findByTicketId(UUID ticketId) {
        return entityManager.createQuery(
                        "SELECT h FROM TicketHistoryEntry h WHERE h.ticketId = :ticketId ORDER BY h.occurredAt",
                        TicketHistoryEntry.class)
                .setParameter("ticketId", ticketId)
                .getResultList();
    }
}
