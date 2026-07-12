package com.eventflow.modules.ticketing.application.result;

import com.eventflow.modules.ticketing.domain.TicketType;

/** Tarifa + nombre de zona resuelto (para el TicketTypeDto congelado). */
public record TicketTypeResult(TicketType ticketType, String zoneName) {
}
