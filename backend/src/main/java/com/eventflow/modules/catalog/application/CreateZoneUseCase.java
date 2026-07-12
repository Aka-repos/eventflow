package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.catalog.domain.port.EventZoneRepository;
import com.eventflow.shared.error.SemanticValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateZoneUseCase {

    private final EventZoneRepository zoneRepository;
    private final CatalogValidations validations;

    public CreateZoneUseCase(EventZoneRepository zoneRepository, CatalogValidations validations) {
        this.zoneRepository = zoneRepository;
        this.validations = validations;
    }

    @Transactional
    public EventZone execute(UUID organizerId, UUID eventId, String name, int capacity) {
        validations.requireOwnedEvent(eventId, organizerId);
        boolean nameTaken = zoneRepository.findByEventId(eventId).stream()
                .anyMatch(z -> z.getName().equalsIgnoreCase(name));
        if (nameTaken) {
            throw new SemanticValidationException("name", "Ya existe una zona con ese nombre en el evento");
        }
        return zoneRepository.save(EventZone.create(eventId, name, capacity));
    }
}
