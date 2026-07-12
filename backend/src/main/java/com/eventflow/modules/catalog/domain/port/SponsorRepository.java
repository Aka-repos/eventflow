package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.Sponsor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SponsorRepository {

    Sponsor save(Sponsor sponsor);

    Optional<Sponsor> findById(UUID id);

    /** Sponsors vinculados a un evento (tabla sponsor_events). */
    List<Sponsor> findByEventId(UUID eventId);

    void delete(Sponsor sponsor);
}
