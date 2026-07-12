package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.exception.TicketTypeNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteTicketTypeUseCase {

    private final TicketTypeRepository repository;
    private final CatalogFacade catalog;

    public DeleteTicketTypeUseCase(TicketTypeRepository repository, CatalogFacade catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional
    public void execute(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        catalog.ensureEventOwnedBy(eventId, organizerId);
        TicketType ticketType = repository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(TicketTypeNotFoundException::new);
        ticketType.ensureDeletable();
        repository.delete(ticketType);
    }
}
