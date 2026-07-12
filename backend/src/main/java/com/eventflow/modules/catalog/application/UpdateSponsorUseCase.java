package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Sponsor;
import com.eventflow.modules.catalog.domain.exception.SponsorNotFoundException;
import com.eventflow.modules.catalog.domain.port.SponsorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateSponsorUseCase {

    private final SponsorRepository sponsorRepository;

    public UpdateSponsorUseCase(SponsorRepository sponsorRepository) {
        this.sponsorRepository = sponsorRepository;
    }

    @Transactional
    public Sponsor execute(UUID id, String name, String logoUrl, String website) {
        Sponsor sponsor = sponsorRepository.findById(id).orElseThrow(SponsorNotFoundException::new);
        sponsor.update(name, logoUrl, website);
        return sponsorRepository.save(sponsor);
    }
}
