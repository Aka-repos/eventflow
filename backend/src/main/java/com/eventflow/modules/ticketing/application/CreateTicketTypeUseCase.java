package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.modules.ticketing.application.result.TicketTypeResult;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.port.TicketTypeRepository;
import com.eventflow.shared.error.SemanticValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTicketTypeUseCase {

    private final TicketTypeRepository repository;
    private final CatalogFacade catalog;

    public CreateTicketTypeUseCase(TicketTypeRepository repository, CatalogFacade catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional
    public TicketTypeResult execute(TicketTypeCommand cmd) {
        catalog.ensureEventOwnedBy(cmd.eventId(), cmd.organizerId());
        String zoneName = resolveZoneName(cmd);
        TicketType saved = repository.save(TicketType.create(cmd.eventId(), cmd.name(), cmd.description(),
                cmd.price(), cmd.zoneId(), cmd.totalQuantity(), cmd.salesStartsAt(), cmd.salesEndsAt()));
        return new TicketTypeResult(saved, zoneName);
    }

    private String resolveZoneName(TicketTypeCommand cmd) {
        if (cmd.zoneId() == null) {
            return null;
        }
        return catalog.zoneNameForEvent(cmd.zoneId(), cmd.eventId())
                .orElseThrow(() -> new SemanticValidationException("zoneId", "La zona no pertenece al evento"));
    }
}
