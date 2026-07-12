package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

/** Proyección de lectura para EventSummary (incluye datos calculados por SQL). */
public record EventListItem(UUID id, String title, String venueName, Instant startsAt, Instant endsAt,
                            String timezone, EventStatus status, String coverUrl,
                            short categoryId, String categoryName, String categoryIcon,
                            Money priceFrom, Boolean isFavorite) {
}
