package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.command.UpdateEventCommand;
import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.event.EventRescheduled;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.shared.error.VersionConflictException;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class UpdateEventUseCase {

    private final EventRepository eventRepository;
    private final CatalogValidations validations;
    private final EventDetailAssembler assembler;
    private final OutboxPublisher outbox;

    public UpdateEventUseCase(EventRepository eventRepository, CatalogValidations validations,
                              EventDetailAssembler assembler, OutboxPublisher outbox) {
        this.eventRepository = eventRepository;
        this.validations = validations;
        this.assembler = assembler;
        this.outbox = outbox;
    }

    @Transactional
    public EventDetailResult execute(UpdateEventCommand cmd) {
        Event event = validations.requireOwnedEvent(cmd.eventId(), cmd.organizerId());
        if (event.getVersion() != cmd.ifMatchVersion()) {
            throw new VersionConflictException(event.getVersion());
        }
        if (cmd.categoryIdForValidation() != null) {
            validations.requireActiveCategory(cmd.categoryIdForValidation());
        }
        if (cmd.timezoneForValidation() != null) {
            validations.requireValidTimezone(cmd.timezoneForValidation());
        }
        Instant previousStarts = event.getStartsAt();
        Instant previousEnds = event.getEndsAt();
        boolean rescheduled = event.applyUpdate(cmd.update());
        Event saved = eventRepository.save(event);
        if (rescheduled) {
            outbox.publish("Event", saved.getId(), EventRescheduled.TYPE, EventRescheduled.VERSION,
                    cmd.organizerId(), Map.of(
                            "eventId", saved.getId().toString(),
                            "previousStartsAt", previousStarts.toString(),
                            "previousEndsAt", previousEnds.toString(),
                            "newStartsAt", saved.getStartsAt().toString(),
                            "newEndsAt", saved.getEndsAt().toString()));
        }
        return assembler.assembleForOwner(saved);
    }
}
