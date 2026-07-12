package com.eventflow.modules.catalog.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Domain event público (api/08): cambio de horario de un evento publicado. */
public record EventRescheduled(UUID eventId, Instant previousStartsAt, Instant previousEndsAt,
                               Instant newStartsAt, Instant newEndsAt) {

    public static final String TYPE = "EventRescheduled";
    public static final int VERSION = 1;
}
