package com.eventflow.modules.identity.domain.port;

import com.eventflow.modules.identity.domain.StaffAssignment;

import java.util.Optional;
import java.util.UUID;

public interface StaffAssignmentRepository {

    StaffAssignment save(StaffAssignment assignment);

    Optional<StaffAssignment> findActive(UUID eventId, UUID userId);

    boolean existsActive(UUID eventId, UUID userId);
}
