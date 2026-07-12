package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.application.result.TicketView;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketStatus;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import com.eventflow.shared.web.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListMyTicketsUseCase {

    private final TicketRepository ticketRepository;
    private final TicketViewAssembler assembler;

    public ListMyTicketsUseCase(TicketRepository ticketRepository, TicketViewAssembler assembler) {
        this.ticketRepository = ticketRepository;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public CursorPage<TicketView> execute(UUID ownerId, TicketStatus status, String cursor, int limit) {
        CursorPage<Ticket> page = ticketRepository.findByOwner(ownerId, status, cursor, limit);
        List<TicketView> views = page.items().stream()
                .map(ticket -> assembler.assemble(ticket, false))
                .toList();
        return new CursorPage<>(views, page.nextCursor());
    }
}
