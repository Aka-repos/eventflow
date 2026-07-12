package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.Sponsor;
import com.eventflow.modules.catalog.domain.port.SponsorRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaSponsorRepositoryAdapter implements SponsorRepository {

    private final SpringDataSponsorRepository jpa;

    JpaSponsorRepositoryAdapter(SpringDataSponsorRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Sponsor save(Sponsor sponsor) {
        return jpa.saveAndFlush(sponsor);
    }

    @Override
    public Optional<Sponsor> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public List<Sponsor> findByEventId(UUID eventId) {
        return jpa.findLinkedToEvent(eventId);
    }

    @Override
    public void delete(Sponsor sponsor) {
        jpa.delete(sponsor);
        jpa.flush();
    }
}
