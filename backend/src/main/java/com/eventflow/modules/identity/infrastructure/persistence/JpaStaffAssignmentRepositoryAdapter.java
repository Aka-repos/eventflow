package com.eventflow.modules.identity.infrastructure.persistence;

import com.eventflow.modules.identity.domain.StaffAssignment;
import com.eventflow.modules.identity.domain.port.StaffAssignmentRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class JpaStaffAssignmentRepositoryAdapter implements StaffAssignmentRepository {

    private final SpringDataStaffAssignmentRepository jpa;

    JpaStaffAssignmentRepositoryAdapter(SpringDataStaffAssignmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StaffAssignment save(StaffAssignment assignment) {
        return jpa.saveAndFlush(assignment);
    }

    @Override
    public Optional<StaffAssignment> findActive(UUID eventId, UUID userId) {
        return jpa.findByEventIdAndUserIdAndRevokedAtIsNull(eventId, userId);
    }

    @Override
    public boolean existsActive(UUID eventId, UUID userId) {
        return jpa.existsByEventIdAndUserIdAndRevokedAtIsNull(eventId, userId);
    }
}
