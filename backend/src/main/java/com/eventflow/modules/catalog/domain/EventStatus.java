package com.eventflow.modules.catalog.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados del evento (design/04 §3):
 * DRAFT → PUBLISHED → (SOLD_OUT ⇄ PUBLISHED) → IN_PROGRESS → FINISHED, más CANCELLED y SUSPENDED.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    SOLD_OUT,
    IN_PROGRESS,
    FINISHED,
    CANCELLED,
    SUSPENDED;

    private Set<EventStatus> next() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PUBLISHED);
            case PUBLISHED -> EnumSet.of(SOLD_OUT, IN_PROGRESS, CANCELLED, SUSPENDED);
            case SOLD_OUT -> EnumSet.of(PUBLISHED, IN_PROGRESS, CANCELLED, SUSPENDED);
            case IN_PROGRESS -> EnumSet.of(FINISHED, CANCELLED, SUSPENDED);
            case SUSPENDED -> EnumSet.of(PUBLISHED, CANCELLED);
            case FINISHED, CANCELLED -> EnumSet.noneOf(EventStatus.class);
        };
    }

    public boolean canTransitionTo(EventStatus target) {
        return next().contains(target);
    }

    /** Estados visibles en el catálogo público (búsqueda/listado). */
    public boolean isPubliclyListed() {
        return this == PUBLISHED || this == SOLD_OUT || this == IN_PROGRESS;
    }
}
