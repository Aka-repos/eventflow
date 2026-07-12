package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.EventZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataEventZoneRepository extends JpaRepository<EventZone, UUID> {

    Optional<EventZone> findByIdAndEventId(UUID id, UUID eventId);

    List<EventZone> findByEventIdOrderByName(UUID eventId);
}
