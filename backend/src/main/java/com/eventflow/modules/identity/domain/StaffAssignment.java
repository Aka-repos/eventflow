package com.eventflow.modules.identity.domain;

import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Staff de acceso (ADR-13): un usuario autorizado por el organizador a escanear QR de un evento.
 * Vive en identity aunque referencia events. El índice único parcial impide dos asignaciones
 * activas del mismo usuario al mismo evento.
 */
@Entity
@Table(name = "staff_assignments", schema = "identity")
public class StaffAssignment {

    private static final String CHECKIN_EVENT = "CHECKIN_EVENT";

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text[]")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    private String[] permissions;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected StaffAssignment() {
    }

    private StaffAssignment(UUID id, UUID eventId, UUID userId, String[] permissions, UUID assignedBy) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.permissions = permissions;
        this.assignedBy = assignedBy;
    }

    public static StaffAssignment assign(UUID eventId, UUID userId, List<String> permissions, UUID assignedBy) {
        String[] perms = (permissions == null || permissions.isEmpty())
                ? new String[] {CHECKIN_EVENT}
                : permissions.toArray(String[]::new);
        return new StaffAssignment(Uuids.v7(), eventId, userId, perms, assignedBy);
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean canCheckInEvent() {
        if (!isActive()) {
            return false;
        }
        for (String p : permissions) {
            if (CHECKIN_EVENT.equals(p)) {
                return true;
            }
        }
        return false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUserId() {
        return userId;
    }
}
