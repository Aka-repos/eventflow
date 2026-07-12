package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.EventZone;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventZoneRepository {

    EventZone save(EventZone zone);

    Optional<EventZone> findByIdAndEventId(UUID id, UUID eventId);

    List<EventZone> findByEventId(UUID eventId);

    void delete(EventZone zone);
}
