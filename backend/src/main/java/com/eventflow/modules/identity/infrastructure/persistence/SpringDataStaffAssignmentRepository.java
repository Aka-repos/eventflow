package com.eventflow.modules.identity.infrastructure.persistence;

import com.eventflow.modules.identity.domain.StaffAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataStaffAssignmentRepository extends JpaRepository<StaffAssignment, UUID> {

    Optional<StaffAssignment> findByEventIdAndUserIdAndRevokedAtIsNull(UUID eventId, UUID userId);

    boolean existsByEventIdAndUserIdAndRevokedAtIsNull(UUID eventId, UUID userId);
}
