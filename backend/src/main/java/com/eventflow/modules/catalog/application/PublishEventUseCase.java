package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.event.EventPublished;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.modules.catalog.domain.port.TariffsReadPort;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PublishEventUseCase {

    private final EventRepository eventRepository;
    private final TariffsReadPort tariffs;
    private final CatalogValidations validations;
    private final EventDetailAssembler assembler;
    private final OutboxPublisher outbox;

    public PublishEventUseCase(EventRepository eventRepository, TariffsReadPort tariffs,
                               CatalogValidations validations, EventDetailAssembler assembler,
                               OutboxPublisher outbox) {
        this.eventRepository = eventRepository;
        this.tariffs = tariffs;
        this.validations = validations;
        this.assembler = assembler;
        this.outbox = outbox;
    }

    @Transactional
    public EventDetailResult execute(UUID organizerId, UUID eventId) {
        Event event = validations.requireOwnedEvent(eventId, organizerId);
        event.publish(tariffs.eventHasTariffs(eventId));
        Event saved = eventRepository.save(event);
        outbox.publish("Event", saved.getId(), EventPublished.TYPE, EventPublished.VERSION,
                organizerId, Map.of(
                        "eventId", saved.getId().toString(),
                        "title", saved.getTitle(),
                        "startsAt", saved.getStartsAt().toString(),
                        "endsAt", saved.getEndsAt().toString()));
        return assembler.assembleForOwner(saved);
    }
}
