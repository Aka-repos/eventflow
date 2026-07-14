package com.eventflow.modules.refunds.infrastructure.persistence;

import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.refunds.domain.RefundStatus;
import com.eventflow.modules.refunds.domain.exception.RefundAlreadyRequestedException;
import com.eventflow.modules.refunds.domain.port.RefundRequestRepository;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.Cursors;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaRefundRequestRepositoryAdapter implements RefundRequestRepository {

    private final SpringDataRefundRequestRepository jpa;
    private final EntityManager entityManager;

    JpaRefundRequestRepositoryAdapter(SpringDataRefundRequestRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    /** uq_refunds_ticket_active: dos solicitudes concurrentes del mismo boleto → conflicto (409). */
    @Override
    public RefundRequest save(RefundRequest refund) {
        try {
            return jpa.saveAndFlush(refund);
        } catch (DataIntegrityViolationException ex) {
            throw new RefundAlreadyRequestedException();
        }
    }

    @Override
    public Optional<RefundRequest> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<RefundRequest> findByIdForUpdate(UUID id) {
        return jpa.lockById(id);
    }

    @Override
    public boolean existsActiveForTicket(UUID ticketId) {
        return jpa.existsByTicketIdAndStatus(ticketId, RefundStatus.REQUESTED);
    }

    /**
     * Reembolsos de un evento (bandeja del organizador). Une con tickets para filtrar por evento
     * (refund_requests no tiene event_id; el vínculo es ticket→event). Keyset (created_at, id) DESC.
     */
    @Override
    public CursorPage<RefundRequest> findByEvent(UUID eventId, RefundStatus status, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id FROM commerce.refund_requests r
                JOIN ticketing.tickets t ON t.id = r.ticket_id
                WHERE t.event_id = :eventId AND r.deleted_at IS NULL
                """);
        if (status != null) {
            sql.append(" AND r.status = :status");
        }
        Cursors.Key key = cursor == null ? null : Cursors.decode(cursor, true);
        if (key != null) {
            sql.append(" AND (r.created_at < :curTs OR (r.created_at = :curTs AND r.id < :curId))");
        }
        sql.append(" ORDER BY r.created_at DESC, r.id DESC LIMIT :lim");
        var query = entityManager.createNativeQuery(sql.toString())
                .setParameter("eventId", eventId)
                .setParameter("lim", limit + 1);
        if (status != null) {
            query.setParameter("status", status.name());
        }
        if (key != null) {
            query.setParameter("curTs", Instant.ofEpochMilli(key.sortMillis()));
            query.setParameter("curId", key.id());
        }
        @SuppressWarnings("unchecked")
        List<UUID> ids = query.getResultList();
        boolean hasNext = ids.size() > limit;
        if (hasNext) {
            ids = ids.subList(0, limit);
        }
        List<RefundRequest> rows = ids.stream().map(id -> entityManager.find(RefundRequest.class, id)).toList();
        String nextCursor = null;
        if (hasNext && !rows.isEmpty()) {
            RefundRequest last = rows.get(rows.size() - 1);
            nextCursor = Cursors.encode(last.getCreatedAt().toEpochMilli(), last.getId(), true);
        }
        return new CursorPage<>(rows, nextCursor);
    }
}
