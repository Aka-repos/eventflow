package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Sponsor;
import com.eventflow.modules.catalog.domain.exception.SponsorNotFoundException;
import com.eventflow.modules.catalog.domain.port.SponsorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteSponsorUseCase {

    private final SponsorRepository sponsorRepository;

    public DeleteSponsorUseCase(SponsorRepository sponsorRepository) {
        this.sponsorRepository = sponsorRepository;
    }

    @Transactional
    public void execute(UUID id) {
        Sponsor sponsor = sponsorRepository.findById(id).orElseThrow(SponsorNotFoundException::new);
        sponsorRepository.delete(sponsor);
    }
}
