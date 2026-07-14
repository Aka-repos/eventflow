package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.domain.StaffAssignment;
import com.eventflow.modules.identity.domain.port.StaffAssignmentRepository;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** ÚNICA superficie de identity para otros módulos (doc 10, S¹/S⁷: usuarios y staff). */
@Component
public class IdentityFacade {

    private final UserRepository userRepository;
    private final StaffAssignmentRepository staffRepository;

    public IdentityFacade(UserRepository userRepository, StaffAssignmentRepository staffRepository) {
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
    }

    @Transactional(readOnly = true)
    public String userDisplayName(UUID userId) {
        return userRepository.findById(userId).map(u -> u.getFullName()).orElse("");
    }

    @Transactional(readOnly = true)
    public Optional<UUID> userIdByEmail(String email) {
        return userRepository.findByEmail(normalize(email)).map(u -> u.getId());
    }

    /**
     * S⁷ (checkin→identity): ¿este usuario puede escanear este evento? El organizador dueño del
     * evento también puede (lo resuelve el llamador vía catalog); aquí solo el staff asignado.
     */
    @Transactional(readOnly = true)
    public boolean isActiveStaff(UUID eventId, UUID userId) {
        return staffRepository.findActive(eventId, userId)
                .map(StaffAssignment::canCheckInEvent)
                .orElse(false);
    }

    /** Persiste una asignación de staff (idempotente si ya está activa). La autorización del
     *  organizador la hace el llamador (checkin, que sí puede consultar catalog). */
    @Transactional
    public void assignStaff(UUID eventId, UUID staffUserId, java.util.List<String> permissions,
                            UUID assignedBy) {
        if (!staffRepository.existsActive(eventId, staffUserId)) {
            staffRepository.save(StaffAssignment.assign(eventId, staffUserId, permissions, assignedBy));
        }
    }

    @Transactional
    public void revokeStaff(UUID eventId, UUID staffUserId, java.time.Instant now) {
        staffRepository.findActive(eventId, staffUserId).ifPresent(a -> {
            a.revoke(now);
            staffRepository.save(a);
        });
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
