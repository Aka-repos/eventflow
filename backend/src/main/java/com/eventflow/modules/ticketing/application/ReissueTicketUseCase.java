package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketHistoryEntry;
import com.eventflow.modules.ticketing.domain.exception.TicketBlockedException;
import com.eventflow.modules.ticketing.domain.exception.TicketNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketHistoryRepository;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * POST /organizer/tickets/{id}/reissue: invalida el QR anterior y deja el boleto listo para
 * generar uno nuevo (QR robado/perdido). El boleto debe seguir ACTIVE.
 */
@Service
public class ReissueTicketUseCase {

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final QrIssuer qrIssuer;
    private final CatalogFacade catalog;

    public ReissueTicketUseCase(TicketRepository ticketRepository, TicketHistoryRepository historyRepository,
                                QrIssuer qrIssuer, CatalogFacade catalog) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.qrIssuer = qrIssuer;
        this.catalog = catalog;
    }

    @Transactional
    public Ticket execute(UUID organizerId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(TicketNotFoundException::new);
        catalog.ensureEventOwnedBy(ticket.getEventId(), organizerId);
        if (!ticket.isCheckInEligible()) {
            throw new TicketBlockedException("Solo un boleto ACTIVE puede reemitirse (estado: "
                    + ticket.getStatus() + ")");
        }
        // invalida el QR vivo; el próximo GET /qr generará uno nuevo (un solo QR vivo por boleto)
        qrIssuer.invalidateLive(ticket, "REISSUE", organizerId);
        historyRepository.append(TicketHistoryEntry.of(ticket.getId(), ticket.getStatus().name(),
                ticket.getStatus().name(), "REISSUE", organizerId));
        return ticket;
    }
}
