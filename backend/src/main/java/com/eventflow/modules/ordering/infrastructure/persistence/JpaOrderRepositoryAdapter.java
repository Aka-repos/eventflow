package com.eventflow.modules.ordering.infrastructure.persistence;

import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.Cursors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaOrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository jpa;
    private final EntityManager entityManager;

    JpaOrderRepositoryAdapter(SpringDataOrderRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Order save(Order order) {
        return jpa.saveAndFlush(order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID id) {
        return jpa.lockById(id);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(UUID idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<UUID> findOrderIdByItemId(UUID orderItemId) {
        var result = entityManager.createNativeQuery(
                        "SELECT order_id FROM commerce.order_items WHERE id = :id")
                .setParameter("id", orderItemId)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of((UUID) result.get(0));
    }

    /** Keyset (created_at DESC, id DESC) sobre ix_orders_buyer — api/07. */
    @Override
    public CursorPage<Order> findByBuyer(UUID buyerId, OrderStatus status, String cursor, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT o FROM Order o WHERE o.buyerId = :buyerId");
        if (status != null) {
            jpql.append(" AND o.status = :status");
        }
        Cursors.Key key = cursor == null ? null : Cursors.decode(cursor, true);
        if (key != null) {
            jpql.append(" AND (o.createdAt < :curCreated OR (o.createdAt = :curCreated AND o.id < :curId))");
        }
        jpql.append(" ORDER BY o.createdAt DESC, o.id DESC");
        TypedQuery<Order> query = entityManager.createQuery(jpql.toString(), Order.class)
                .setParameter("buyerId", buyerId)
                .setMaxResults(limit + 1);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (key != null) {
            query.setParameter("curCreated", Instant.ofEpochMilli(key.sortMillis()));
            query.setParameter("curId", key.id());
        }
        List<Order> rows = query.getResultList();
        String nextCursor = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            Order last = rows.get(rows.size() - 1);
            nextCursor = Cursors.encode(last.getCreatedAt().toEpochMilli(), last.getId(), true);
        }
        return new CursorPage<>(List.copyOf(rows), nextCursor);
    }

    /** ix_orders_pending + FOR UPDATE SKIP LOCKED: lotes concurrentes sin pisarse (M7). */
    @Override
    public List<Order> lockExpiredPending(Instant now, int batchSize) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery("""
                        SELECT o.id FROM commerce.orders o
                        WHERE o.status = 'PENDING' AND o.expires_at < :now AND o.deleted_at IS NULL
                          -- guard H1: cobro en vuelo o liquidado ⇒ la reconciliación decide, no la expiración
                          AND NOT EXISTS (SELECT 1 FROM commerce.payments p
                                          WHERE p.order_id = o.id
                                            AND p.status IN ('PENDING','APPROVED','REFUNDED'))
                        ORDER BY o.expires_at
                        LIMIT :batch
                        FOR UPDATE SKIP LOCKED
                        """)
                .setParameter("now", now)
                .setParameter("batch", batchSize)
                .getResultList();
        return ids.stream().map(id -> entityManager.find(Order.class, id)).toList();
    }

    @Override
    public List<UUID> findPendingCreatedBefore(Instant cutoff, int limit) {
        @SuppressWarnings("unchecked")
        List<UUID> ids = entityManager.createNativeQuery("""
                        SELECT id FROM commerce.orders
                        WHERE status = 'PENDING' AND created_at < :cutoff AND deleted_at IS NULL
                        ORDER BY created_at
                        LIMIT :lim
                        """)
                .setParameter("cutoff", cutoff)
                .setParameter("lim", limit)
                .getResultList();
        return ids;
    }
}
