package com.eventflow.modules.catalog.application.command;

import java.time.Instant;
import java.util.UUID;

public record CreateEventCommand(UUID organizerId, String title, String description, int categoryId,
                                 String venueName, String address, Double latitude, Double longitude,
                                 String timezone, Instant startsAt, Instant endsAt) {
}
