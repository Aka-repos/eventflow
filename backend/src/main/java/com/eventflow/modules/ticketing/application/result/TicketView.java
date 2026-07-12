package com.eventflow.modules.ticketing.application.result;

import com.eventflow.modules.catalog.application.CatalogFacade.EventCard;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;

import java.util.List;

/** Boleto + tarjeta del evento (S²) + nombre de tarifa; history solo en el detalle. */
public record TicketView(Ticket ticket, EventCard event, String ticketTypeName, String zoneName,
                         List<TicketHistoryEntry> history) {
}
