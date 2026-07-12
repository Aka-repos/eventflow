package com.eventflow.modules.catalog.application.command;

import com.eventflow.modules.catalog.domain.EventUpdate;

import java.util.UUID;

public record UpdateEventCommand(UUID organizerId, UUID eventId, int ifMatchVersion, EventUpdate update,
                                 Integer categoryIdForValidation, String timezoneForValidation) {
}
