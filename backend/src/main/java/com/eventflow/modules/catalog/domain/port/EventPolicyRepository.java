package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.EventPolicy;

import java.util.Optional;
import java.util.UUID;

public interface EventPolicyRepository {

    EventPolicy save(EventPolicy policy);

    Optional<EventPolicy> findByEventId(UUID eventId);
}
