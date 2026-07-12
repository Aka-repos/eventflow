package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.port.EventRepository;
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
class JpaEventRepositoryAdapter implements EventRepository {

    private final SpringDataEventRepository jpa;
    private final EntityManager entityManager;

    JpaEventRepositoryAdapter(SpringDataEventRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Event save(Event event) {
        return jpa.saveAndFlush(event);
    }

    @Override
    public Optional<Event> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    public boolean existsByCategoryId(short categoryId) {
        return jpa.existsByCategoryId(categoryId);
    }

    /** Keyset (starts_at, id) ascendente — api/07. */
    @Override
    public CursorPage<Event> findByOrganizer(UUID organizerId, EventStatus status, String cursor, int limit) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e WHERE e.organizerId = :organizerId");
        if (status != null) {
            jpql.append(" AND e.status = :status");
        }
        Cursors.Key key = cursor == null ? null : Cursors.decode(cursor, false);
        if (key != null) {
            jpql.append(" AND (e.startsAt > :curStarts OR (e.startsAt = :curStarts AND e.id > :curId))");
        }
        jpql.append(" ORDER BY e.startsAt ASC, e.id ASC");
        TypedQuery<Event> query = entityManager.createQuery(jpql.toString(), Event.class)
                .setParameter("organizerId", organizerId)
                .setMaxResults(limit + 1);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (key != null) {
            query.setParameter("curStarts", Instant.ofEpochMilli(key.sortMillis()));
            query.setParameter("curId", key.id());
        }
        List<Event> rows = query.getResultList();
        String nextCursor = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            Event last = rows.get(rows.size() - 1);
            nextCursor = Cursors.encode(last.getStartsAt().toEpochMilli(), last.getId(), false);
        }
        return new CursorPage<>(List.copyOf(rows), nextCursor);
    }
}
