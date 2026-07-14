package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.domain.DynamicQr;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.exception.TicketNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * GET /tickets/{id}/qr: solo el dueño, solo dentro de la ventana de visibilidad (ADR-03),
 * solo si el boleto está ACTIVE. Devuelve el QR presentable (reusa o rota).
 */
@Service
public class GetTicketQrUseCase {

    private final TicketRepository ticketRepository;
    private final QrIssuer qrIssuer;
    private final Clock clock;

    public GetTicketQrUseCase(TicketRepository ticketRepository, QrIssuer qrIssuer, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.qrIssuer = qrIssuer;
        this.clock = clock;
    }

    @Transactional
    public QrIssuer.IssuedQr execute(UUID ownerId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.isOwnedBy(ownerId))
                .orElseThrow(TicketNotFoundException::new);
        DynamicQr.ensureVisible(ticket.qrAvailableAt(), clock.instant());
        return qrIssuer.issueOrReuse(ticket, 0);
    }
}
