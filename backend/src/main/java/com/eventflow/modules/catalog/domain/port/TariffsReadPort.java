package com.eventflow.modules.catalog.domain.port;

import com.eventflow.shared.domain.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Proyección de SOLO LECTURA sobre las tarifas (schema ticketing) para componer
 * EventDetail y validar publish/zonas. La matriz doc 10 prohíbe catalog→ticketing por
 * fachada; esta lectura SQL propia no importa código de ticketing (README del módulo).
 */
public interface TariffsReadPort {

    boolean eventHasTariffs(UUID eventId);

    boolean zoneHasTariffs(UUID zoneId);

    List<TariffView> findByEventId(UUID eventId);

    record TariffView(UUID id, String name, String description, Money price, String zoneName,
                      int totalQuantity, int soldQuantity, Instant salesStartsAt, Instant salesEndsAt) {

        public boolean isAvailable(Instant now) {
            boolean hasStock = soldQuantity < totalQuantity;
            boolean windowOpen = (salesStartsAt == null || !now.isBefore(salesStartsAt))
                    && (salesEndsAt == null || now.isBefore(salesEndsAt));
            return hasStock && windowOpen;
        }
    }
}
