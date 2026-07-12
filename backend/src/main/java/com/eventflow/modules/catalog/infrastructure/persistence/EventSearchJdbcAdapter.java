package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.modules.catalog.domain.port.EventSearchPort;
import com.eventflow.modules.catalog.domain.port.EventSearchQuery;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.Cursors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consulta pública del catálogo (GET /events): full-text sobre search_vector (índice GIN),
 * filtro geográfico haversine, keyset (starts_at, id) según api/07 y datos calculados
 * (priceFrom = tarifa mínima; isFavorite solo con viewer autenticado).
 */
@Component
class EventSearchJdbcAdapter implements EventSearchPort {

    private static final String BASE_SELECT = """
            SELECT e.id, e.title, e.venue_name, e.starts_at, e.ends_at, e.timezone, e.status, e.cover_url,
                   c.id AS cat_id, c.name AS cat_name, c.icon AS cat_icon,
                   pf.amount AS price_from_amount, pf.currency AS price_from_currency,
                   %s AS is_favorite
            FROM catalog.events e
            JOIN catalog.categories c ON c.id = e.category_id
            LEFT JOIN LATERAL (
                SELECT tt.price AS amount, tt.currency
                FROM ticketing.ticket_types tt
                WHERE tt.event_id = e.id
                ORDER BY tt.price ASC
                LIMIT 1
            ) pf ON true
            """;

    private final EntityManager entityManager;

    EventSearchJdbcAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CursorPage<EventListItem> search(EventSearchQuery q) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder sql = new StringBuilder(BASE_SELECT.formatted(favoriteExpression(q.viewerId(), params)));
        sql.append(" WHERE e.deleted_at IS NULL AND e.status IN ('PUBLISHED','SOLD_OUT','IN_PROGRESS')");
        if (q.q() != null && !q.q().isBlank()) {
            sql.append(" AND e.search_vector @@ plainto_tsquery('spanish', :query)");
            params.put("query", q.q());
        }
        if (q.categoryId() != null) {
            sql.append(" AND e.category_id = :categoryId");
            params.put("categoryId", q.categoryId().shortValue());
        }
        if (q.dateFrom() != null) {
            sql.append(" AND e.starts_at >= :dateFrom");
            params.put("dateFrom", q.dateFrom());
        }
        if (q.dateTo() != null) {
            sql.append(" AND e.starts_at <= :dateTo");
            params.put("dateTo", q.dateTo());
        }
        if (q.nearLat() != null && q.nearLng() != null) {
            sql.append(" AND e.latitude IS NOT NULL AND 6371 * acos(least(1.0, greatest(-1.0,")
                    .append(" cos(radians(:nearLat)) * cos(radians(e.latitude)) *")
                    .append(" cos(radians(e.longitude) - radians(:nearLng)) +")
                    .append(" sin(radians(:nearLat)) * sin(radians(e.latitude))))) <= :radiusKm");
            params.put("nearLat", q.nearLat());
            params.put("nearLng", q.nearLng());
            params.put("radiusKm", q.radiusKm());
        }
        boolean desc = q.descending();
        if (q.cursor() != null) {
            Cursors.Key key = Cursors.decode(q.cursor(), desc);
            sql.append(desc
                    ? " AND (e.starts_at < :curStarts OR (e.starts_at = :curStarts AND e.id < :curId))"
                    : " AND (e.starts_at > :curStarts OR (e.starts_at = :curStarts AND e.id > :curId))");
            params.put("curStarts", Instant.ofEpochMilli(key.sortMillis()));
            params.put("curId", key.id());
        }
        sql.append(desc ? " ORDER BY e.starts_at DESC, e.id DESC" : " ORDER BY e.starts_at ASC, e.id ASC");
        sql.append(" LIMIT :limitPlusOne");
        params.put("limitPlusOne", q.limit() + 1);

        List<EventListItem> rows = executeAndMap(sql.toString(), params);
        String nextCursor = null;
        if (rows.size() > q.limit()) {
            rows = rows.subList(0, q.limit());
            EventListItem last = rows.get(rows.size() - 1);
            nextCursor = Cursors.encode(last.startsAt().toEpochMilli(), last.id(), desc);
        }
        return new CursorPage<>(List.copyOf(rows), nextCursor);
    }

    @Override
    public List<EventListItem> favoritesOf(UUID userId) {
        Map<String, Object> params = new HashMap<>();
        String sql = BASE_SELECT.formatted("true")
                + " JOIN ops.favorites f ON f.event_id = e.id AND f.user_id = :userId"
                + " WHERE e.deleted_at IS NULL AND e.status <> 'DRAFT'"
                + " ORDER BY f.created_at DESC";
        params.put("userId", userId);
        return executeAndMap(sql, params);
    }

    private String favoriteExpression(UUID viewerId, Map<String, Object> params) {
        if (viewerId == null) {
            return "CAST(NULL AS boolean)";
        }
        params.put("viewerId", viewerId);
        return "EXISTS (SELECT 1 FROM ops.favorites f WHERE f.user_id = :viewerId AND f.event_id = e.id)";
    }

    private List<EventListItem> executeAndMap(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<Object[]> raw = query.getResultList();
        List<EventListItem> items = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            BigDecimal amount = (BigDecimal) r[11];
            String currency = r[12] == null ? null : ((String) r[12]).trim();
            Money priceFrom = amount == null ? null : new Money(amount, currency);
            items.add(new EventListItem(
                    (UUID) r[0], (String) r[1], (String) r[2],
                    toInstant(r[3]), toInstant(r[4]), (String) r[5],
                    EventStatus.valueOf((String) r[6]), (String) r[7],
                    ((Number) r[8]).shortValue(), (String) r[9], (String) r[10],
                    priceFrom, (Boolean) r[13]));
        }
        return items;
    }

    private static Instant toInstant(Object column) {
        if (column instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return (Instant) column;
    }
}
