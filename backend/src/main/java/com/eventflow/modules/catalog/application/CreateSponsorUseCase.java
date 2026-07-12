package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Sponsor;
import com.eventflow.modules.catalog.domain.port.SponsorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateSponsorUseCase {

    private final SponsorRepository sponsorRepository;

    public CreateSponsorUseCase(SponsorRepository sponsorRepository) {
        this.sponsorRepository = sponsorRepository;
    }

    @Transactional
    public Sponsor execute(String name, String logoUrl, String website) {
        return sponsorRepository.save(Sponsor.create(name, logoUrl, website));
    }
}
