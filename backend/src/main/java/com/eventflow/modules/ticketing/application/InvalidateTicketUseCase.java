package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;
import com.eventflow.modules.ticketing.domain.exception.TicketNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketHistoryRepository;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** POST /organizer/tickets/{id}/invalidate: el boleto pasa a INVALIDATED y su QR muere. */
@Service
public class InvalidateTicketUseCase {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final QrIssuer qrIssuer;
    private final CatalogFacade catalog;

    public InvalidateTicketUseCase(TicketRepository ticketRepository, TicketHistoryRepository historyRepository,
                                   QrIssuer qrIssuer, CatalogFacade catalog) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.qrIssuer = qrIssuer;
        this.catalog = catalog;
    }

    @Transactional
    public Ticket execute(UUID organizerId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        catalog.ensureEventOwnedBy(ticket.getEventId(), organizerId); // 404 si no es su evento
        String from = ticket.getStatus().name();
        ticket.invalidate();
        Ticket saved = ticketRepository.save(ticket);
        qrIssuer.invalidateLive(saved, "INVALIDATE", organizerId);
        historyRepository.append(TicketHistoryEntry.of(saved.getId(), from,
                saved.getStatus().name(), "INVALIDATE", organizerId));
        return saved;
    }
}
