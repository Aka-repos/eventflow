package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.Sponsor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface SpringDataSponsorRepository extends JpaRepository<Sponsor, UUID> {

    @Query(value = "SELECT s.* FROM catalog.sponsors s "
            + "JOIN catalog.sponsor_events se ON se.sponsor_id = s.id "
            + "WHERE se.event_id = :eventId ORDER BY s.name", nativeQuery = true)
    List<Sponsor> findLinkedToEvent(@Param("eventId") UUID eventId);
}
