package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.port.EventPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetEventPolicyUseCase {

    private final EventPolicyRepository policyRepository;
    private final CatalogValidations validations;

    public GetEventPolicyUseCase(EventPolicyRepository policyRepository, CatalogValidations validations) {
        this.policyRepository = policyRepository;
        this.validations = validations;
    }

    @Transactional(readOnly = true)
    public EventPolicy execute(UUID organizerId, UUID eventId) {
        validations.requireOwnedEvent(eventId, organizerId);
        return policyRepository.findByEventId(eventId).orElseThrow(EventNotFoundException::new);
    }
}
