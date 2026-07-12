package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.port.EventPolicyRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class JpaEventPolicyRepositoryAdapter implements EventPolicyRepository {

    private final SpringDataEventPolicyRepository jpa;

    JpaEventPolicyRepositoryAdapter(SpringDataEventPolicyRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EventPolicy save(EventPolicy policy) {
        return jpa.saveAndFlush(policy);
    }

    @Override
    public Optional<EventPolicy> findByEventId(UUID eventId) {
        return jpa.findById(eventId);
    }
}
