package com.eventflow.modules.ticketing.application.command;

import com.eventflow.shared.domain.Money;

import java.time.Instant;
import java.util.UUID;

/** Entrada de create/update de tarifa (CreateTicketTypeRequest congelado). */
public record TicketTypeCommand(UUID organizerId, UUID eventId, String name, String description,
                                Money price, UUID zoneId, int totalQuantity,
                                Instant salesStartsAt, Instant salesEndsAt) {
}
