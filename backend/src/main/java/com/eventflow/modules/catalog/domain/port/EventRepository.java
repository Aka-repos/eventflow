package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.shared.web.CursorPage;

import java.util.Optional;
import java.util.UUID;

/** Puerto del agregado Event. */
public interface EventRepository {

    Event save(Event event);

    Optional<Event> findById(UUID id);

    boolean existsById(UUID id);

    boolean existsByCategoryId(short categoryId);

    CursorPage<Event> findByOrganizer(UUID organizerId, EventStatus status, String cursor, int limit);
}
