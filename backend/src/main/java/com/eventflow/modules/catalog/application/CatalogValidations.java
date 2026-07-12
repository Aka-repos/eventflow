package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.shared.error.SemanticValidationException;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Validaciones semánticas y autorización por recurso compartidas por los use cases del catálogo. */
@Component
public class CatalogValidations {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;

    public CatalogValidations(EventRepository eventRepository, CategoryRepository categoryRepository) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
    }

    /** 404 también si el evento es de otro organizador (anti-enumeración, api/02 §2). */
    public Event requireOwnedEvent(UUID eventId, UUID organizerId) {
        return eventRepository.findById(eventId)
                .filter(e -> e.isOwnedBy(organizerId))
                .orElseThrow(EventNotFoundException::new);
    }

    public short requireActiveCategory(int categoryId) {
        if (categoryId < Short.MIN_VALUE || categoryId > Short.MAX_VALUE) {
            throw new SemanticValidationException("categoryId", "La categoría no existe");
        }
        Category category = categoryRepository.findById((short) categoryId)
                .orElseThrow(() -> new SemanticValidationException("categoryId", "La categoría no existe"));
        if (!category.isActive()) {
            throw new SemanticValidationException("categoryId", "La categoría no está activa");
        }
        return category.getId();
    }

    public void requireValidTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new SemanticValidationException("timezone", "timezone debe ser un identificador IANA válido");
        }
    }

    public void requireFutureStart(Instant startsAt, Instant now) {
        if (!startsAt.isAfter(now)) {
            throw new SemanticValidationException("startsAt", "startsAt debe estar en el futuro");
        }
    }
}
