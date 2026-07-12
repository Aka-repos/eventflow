package com.eventflow.modules.catalog.domain.port;

import java.time.Instant;
import java.util.UUID;

/** Filtros de GET /events (parámetros congelados del OpenAPI). */
public record EventSearchQuery(String q, Integer categoryId, Instant dateFrom, Instant dateTo,
                               Double nearLat, Double nearLng, double radiusKm,
                               boolean descending, String cursor, int limit, UUID viewerId) {
}
