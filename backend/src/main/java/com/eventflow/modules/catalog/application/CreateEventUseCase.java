package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.port.EventPolicyRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class CreateEventUseCase {

    private final EventRepository eventRepository;
    private final EventPolicyRepository policyRepository;
    private final CatalogValidations validations;
    private final EventDetailAssembler assembler;
    private final Clock clock;

    public CreateEventUseCase(EventRepository eventRepository, EventPolicyRepository policyRepository,
                              CatalogValidations validations, EventDetailAssembler assembler, Clock clock) {
        this.eventRepository = eventRepository;
        this.policyRepository = policyRepository;
        this.validations = validations;
        this.assembler = assembler;
        this.clock = clock;
    }

    @Transactional
    public EventDetailResult execute(CreateEventCommand cmd) {
        short categoryId = validations.requireActiveCategory(cmd.categoryId());
        validations.requireValidTimezone(cmd.timezone());
        validations.requireFutureStart(cmd.startsAt(), clock.instant());
        Event event = Event.create(cmd.organizerId(), categoryId, cmd.title(), cmd.description(),
                cmd.venueName(), cmd.address(), cmd.latitude(), cmd.longitude(),
                cmd.timezone(), cmd.startsAt(), cmd.endsAt());
        Event saved = eventRepository.save(event);
        // La política nace con defaults (espejo V3) para que GET policy y EventDetail siempre resuelvan
        policyRepository.save(EventPolicy.defaultsFor(saved.getId()));
        return assembler.assembleForOwner(saved);
    }
}
