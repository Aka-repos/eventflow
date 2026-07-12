package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.port.TariffsReadPort;
import com.eventflow.shared.domain.Money;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Proyección de SOLO LECTURA sobre ticketing.ticket_types. La matriz doc 10 prohíbe
 * catalog→ticketing por fachada; esta consulta SQL no importa código del módulo ticketing
 * y catalog jamás escribe estas tablas (justificación en el README del módulo).
 */
@Component
class TariffsReadJdbcAdapter implements TariffsReadPort {

    private final EntityManager entityManager;

    TariffsReadJdbcAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean eventHasTariffs(UUID eventId) {
        return !entityManager.createNativeQuery(
                        "SELECT 1 FROM ticketing.ticket_types WHERE event_id = :eventId LIMIT 1")
                .setParameter("eventId", eventId)
                .getResultList().isEmpty();
    }

    @Override
    public boolean zoneHasTariffs(UUID zoneId) {
        return !entityManager.createNativeQuery(
                        "SELECT 1 FROM ticketing.ticket_types WHERE zone_id = :zoneId LIMIT 1")
                .setParameter("zoneId", zoneId)
                .getResultList().isEmpty();
    }

    @Override
    public List<TariffView> findByEventId(UUID eventId) {
        @SuppressWarnings("unchecked")
        List<Object[]> raw = entityManager.createNativeQuery("""
                        SELECT tt.id, tt.name, tt.description, tt.price, tt.currency, z.name AS zone_name,
                               tt.total_quantity, tt.sold_quantity, tt.sales_starts_at, tt.sales_ends_at
                        FROM ticketing.ticket_types tt
                        LEFT JOIN catalog.event_zones z ON z.id = tt.zone_id
                        WHERE tt.event_id = :eventId
                        ORDER BY tt.price ASC, tt.name ASC
                        """)
                .setParameter("eventId", eventId)
                .getResultList();
        List<TariffView> views = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            views.add(new TariffView((UUID) r[0], (String) r[1], (String) r[2],
                    new Money((BigDecimal) r[3], ((String) r[4]).trim()), (String) r[5],
                    ((Number) r[6]).intValue(), ((Number) r[7]).intValue(),
                    toInstant(r[8]), toInstant(r[9])));
        }
        return views;
    }

    private static Instant toInstant(Object column) {
        if (column == null) {
            return null;
        }
        if (column instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return (Instant) column;
    }
}
