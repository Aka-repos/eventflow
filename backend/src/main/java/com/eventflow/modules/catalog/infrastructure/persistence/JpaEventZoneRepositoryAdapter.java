package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.catalog.domain.port.EventZoneRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaEventZoneRepositoryAdapter implements EventZoneRepository {

    private final SpringDataEventZoneRepository jpa;

    JpaEventZoneRepositoryAdapter(SpringDataEventZoneRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EventZone save(EventZone zone) {
        return jpa.saveAndFlush(zone);
    }

    @Override
    public Optional<EventZone> findByIdAndEventId(UUID id, UUID eventId) {
        return jpa.findByIdAndEventId(id, eventId);
    }

    @Override
    public List<EventZone> findByEventId(UUID eventId) {
        return jpa.findByEventIdOrderByName(eventId);
    }

    @Override
    public void delete(EventZone zone) {
        jpa.delete(zone);
    }
}
