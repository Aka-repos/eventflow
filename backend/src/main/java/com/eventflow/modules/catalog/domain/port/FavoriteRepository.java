package com.eventflow.modules.catalog.domain.port;

import java.util.UUID;

public interface FavoriteRepository {

    /** Idempotente: ON CONFLICT DO NOTHING. */
    void add(UUID userId, UUID eventId);

    /** Idempotente. */
    void remove(UUID userId, UUID eventId);

    boolean exists(UUID userId, UUID eventId);
}
