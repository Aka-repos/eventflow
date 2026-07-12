package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.EventPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataEventPolicyRepository extends JpaRepository<EventPolicy, UUID> {
}
