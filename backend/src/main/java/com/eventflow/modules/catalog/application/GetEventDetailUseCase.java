package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetEventDetailUseCase {

    private final EventRepository eventRepository;
    private final EventDetailAssembler assembler;

    public GetEventDetailUseCase(EventRepository eventRepository, EventDetailAssembler assembler) {
        this.eventRepository = eventRepository;
        this.assembler = assembler;
    }

    /** Detalle público: los DRAFT no existen para el público (404). */
    @Transactional(readOnly = true)
    public EventDetailResult execute(UUID eventId, UUID viewerId) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getStatus() != EventStatus.DRAFT)
                .orElseThrow(EventNotFoundException::new);
        return assembler.assemble(event, viewerId);
    }
}
