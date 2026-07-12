package com.eventflow.modules.catalog.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Domain event público (api/08): el evento pasó a PUBLISHED. */
public record EventPublished(UUID eventId, String title, Instant startsAt, Instant endsAt) {

    public static final String TYPE = "EventPublished";
    public static final int VERSION = 1;
}
