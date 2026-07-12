package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.modules.ticketing.application.result.TicketTypeResult;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.exception.TicketTypeNotFoundException;
import com.eventflow.modules.ticketing.domain.port.TicketTypeRepository;
import com.eventflow.shared.error.SemanticValidationException;
import com.eventflow.shared.error.VersionConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateTicketTypeUseCase {

    private final TicketTypeRepository repository;
    private final CatalogFacade catalog;

    public UpdateTicketTypeUseCase(TicketTypeRepository repository, CatalogFacade catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional
    public TicketTypeResult execute(UUID ticketTypeId, int ifMatchVersion, TicketTypeCommand cmd) {
        catalog.ensureEventOwnedBy(cmd.eventId(), cmd.organizerId());
        TicketType ticketType = repository.findByIdAndEventId(ticketTypeId, cmd.eventId())
                .orElseThrow(TicketTypeNotFoundException::new);
        if (ticketType.getVersion() != ifMatchVersion) {
            throw new VersionConflictException(ticketType.getVersion());
        }
        String zoneName = null;
        if (cmd.zoneId() != null) {
            zoneName = catalog.zoneNameForEvent(cmd.zoneId(), cmd.eventId())
                    .orElseThrow(() -> new SemanticValidationException("zoneId", "La zona no pertenece al evento"));
        }
        ticketType.update(cmd.name(), cmd.description(), cmd.price(), cmd.zoneId(),
                cmd.totalQuantity(), cmd.salesStartsAt(), cmd.salesEndsAt());
        return new TicketTypeResult(repository.save(ticketType), zoneName);
    }
}
