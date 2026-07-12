package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.catalog.domain.exception.ZoneInUseException;
import com.eventflow.modules.catalog.domain.exception.ZoneNotFoundException;
import com.eventflow.modules.catalog.domain.port.EventZoneRepository;
import com.eventflow.modules.catalog.domain.port.TariffsReadPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteZoneUseCase {

    private final EventZoneRepository zoneRepository;
    private final TariffsReadPort tariffs;
    private final CatalogValidations validations;

    public DeleteZoneUseCase(EventZoneRepository zoneRepository, TariffsReadPort tariffs,
                             CatalogValidations validations) {
        this.zoneRepository = zoneRepository;
        this.tariffs = tariffs;
        this.validations = validations;
    }

    @Transactional
    public void execute(UUID organizerId, UUID eventId, UUID zoneId) {
        validations.requireOwnedEvent(eventId, organizerId);
        EventZone zone = zoneRepository.findByIdAndEventId(zoneId, eventId)
                .orElseThrow(ZoneNotFoundException::new);
        if (tariffs.zoneHasTariffs(zoneId)) {
            throw new ZoneInUseException();
        }
        zoneRepository.delete(zone);
    }
}
