package com.eventflow.modules.ticketing.domain.port;

import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface TicketHistoryRepository {

    void append(TicketHistoryEntry entry);

    List<TicketHistoryEntry> findByTicketId(UUID ticketId);
}
