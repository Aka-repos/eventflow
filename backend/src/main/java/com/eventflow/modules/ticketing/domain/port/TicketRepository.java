package com.eventflow.modules.ticketing.domain.port;

import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketStatus;
import com.eventflow.shared.web.CursorPage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(UUID id);

    CursorPage<Ticket> findByOwner(UUID ownerId, TicketStatus status, String cursor, int limit);

    /** ticketIds emitidos por ítem de orden (para OrderItemResponse.ticketIds). */
    Map<UUID, List<UUID>> ticketIdsBySourceOrderItem(Collection<UUID> orderItemIds);
}
