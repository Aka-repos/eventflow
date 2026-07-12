package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class DeleteEventUseCase {

    private final EventRepository eventRepository;
    private final CatalogValidations validations;
    private final Clock clock;

    public DeleteEventUseCase(EventRepository eventRepository, CatalogValidations validations, Clock clock) {
        this.eventRepository = eventRepository;
        this.validations = validations;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID organizerId, UUID eventId) {
        Event event = validations.requireOwnedEvent(eventId, organizerId);
        event.softDelete(clock.instant());
        eventRepository.save(event);
    }
}
