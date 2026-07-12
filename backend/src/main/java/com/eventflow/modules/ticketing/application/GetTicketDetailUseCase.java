package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.ticketing.application.result.TicketView;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.exception.TicketNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetTicketDetailUseCase {

    private final TicketRepository ticketRepository;
    private final TicketViewAssembler assembler;

    public GetTicketDetailUseCase(TicketRepository ticketRepository, TicketViewAssembler assembler) {
        this.ticketRepository = ticketRepository;
        this.assembler = assembler;
    }

    /** 404 también si el boleto es de otro usuario (anti-enumeración). */
    @Transactional(readOnly = true)
    public TicketView execute(UUID ownerId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.isOwnedBy(ownerId))
                .orElseThrow(TicketNotFoundException::new);
        return assembler.assemble(ticket, true);
    }
}
