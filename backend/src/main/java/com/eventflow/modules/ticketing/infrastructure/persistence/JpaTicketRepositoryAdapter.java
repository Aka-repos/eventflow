package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketStatus;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.Cursors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaTicketRepositoryAdapter implements TicketRepository {

    private final SpringDataTicketRepository jpa;
    private final EntityManager entityManager;

    JpaTicketRepositoryAdapter(SpringDataTicketRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Ticket save(Ticket ticket) {
        return jpa.saveAndFlush(ticket);
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return jpa.findById(id);
    }

    /** Keyset (purchased_at DESC, id DESC) sobre ix_tickets_owner_status. */
    @Override
    public CursorPage<Ticket> findByOwner(UUID ownerId, TicketStatus status, String cursor, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT t FROM Ticket t WHERE t.currentOwnerId = :ownerId");
        if (status != null) {
            jpql.append(" AND t.status = :status");
        }
        Cursors.Key key = cursor == null ? null : Cursors.decode(cursor, true);
        if (key != null) {
            jpql.append(" AND (t.purchasedAt < :curTs OR (t.purchasedAt = :curTs AND t.id < :curId))");
        }
        jpql.append(" ORDER BY t.purchasedAt DESC, t.id DESC");
        TypedQuery<Ticket> query = entityManager.createQuery(jpql.toString(), Ticket.class)
                .setParameter("ownerId", ownerId)
                .setMaxResults(limit + 1);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (key != null) {
            query.setParameter("curTs", Instant.ofEpochMilli(key.sortMillis()));
            query.setParameter("curId", key.id());
        }
        List<Ticket> rows = query.getResultList();
        String nextCursor = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            Ticket last = rows.get(rows.size() - 1);
            nextCursor = Cursors.encode(last.getPurchasedAt().toEpochMilli(), last.getId(), true);
        }
        return new CursorPage<>(List.copyOf(rows), nextCursor);
    }

    @Override
    public Map<UUID, List<UUID>> ticketIdsBySourceOrderItem(Collection<UUID> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> byItem = new HashMap<>();
        for (Object[] row : jpa.ticketIdsByOrderItem(orderItemIds)) {
            byItem.computeIfAbsent((UUID) row[0], k -> new java.util.ArrayList<>()).add((UUID) row[1]);
        }
        return byItem;
    }
}
