package com.eventflow.modules.catalog.application.result;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.catalog.domain.Sponsor;
import com.eventflow.modules.catalog.domain.port.TariffsReadPort.TariffView;

import java.util.List;

/** Composición completa para EventDetail (organizador viene de identity vía fachada). */
public record EventDetailResult(Event event, Category category, String organizerName,
                                List<TariffView> tariffs, List<EventZone> zones,
                                List<Sponsor> sponsors, EventPolicy policy,
                                Boolean isFavorite, boolean waitlistOpen) {
}
