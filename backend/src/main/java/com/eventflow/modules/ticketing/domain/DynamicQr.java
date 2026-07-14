package com.eventflow.modules.ticketing.domain;

import com.eventflow.modules.ticketing.domain.exception.QrNotYetVisibleException;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Agregado DynamicQr (ADR-08): el id ES el qr_id que viaja dentro del JWS; el token firmado solo
 * lleva qr_id, kid y exp — cero información sensible. La verdad (estado, dueño, ventana) vive aquí.
 * Un índice único parcial garantiza un solo QR ACTIVE/BLOCKED por boleto a nivel de base de datos.
 */
@Entity
@Table(name = "dynamic_qrs", schema = "ticketing")
public class DynamicQr {

    /** Fracción de la vida del QR tras la cual el cliente debe re-solicitarlo (dinámico). */
    private static final double REFRESH_FRACTION = 0.6;

    @Id
    private UUID id;

    @Column(name = "subject_type", nullable = false, updatable = false)
    private String subjectType;

    @Column(name = "ticket_id", updatable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QrStatus status;

    @Column(name = "key_id", nullable = false, updatable = false)
    private String keyId;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected DynamicQr() {
    }

    private DynamicQr(UUID id, UUID ticketId, String keyId, Instant issuedAt, Instant expiresAt) {
        this.id = id;
        this.subjectType = "TICKET";
        this.ticketId = ticketId;
        this.status = QrStatus.ACTIVE;
        this.keyId = keyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public static DynamicQr issueForTicket(UUID ticketId, String keyId, Instant now, int expirationMinutes) {
        return new DynamicQr(Uuids.v7(), ticketId, keyId, now, now.plusSeconds(expirationMinutes * 60L));
    }

    /** Ventana de visibilidad (ADR-03): el QR no se emite antes de qrAvailableAt. */
    public static void ensureVisible(Instant qrAvailableAt, Instant now) {
        if (qrAvailableAt != null && now.isBefore(qrAvailableAt)) {
            throw new QrNotYetVisibleException(qrAvailableAt);
        }
    }

    /** Momento en que el cliente debería re-pedir el QR (antes de expirar). */
    public Instant refreshAfter() {
        long lifeSeconds = expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
        return issuedAt.plusSeconds((long) (lifeSeconds * REFRESH_FRACTION));
    }

    public boolean isValidAt(Instant now) {
        return status == QrStatus.ACTIVE && now.isBefore(expiresAt);
    }

    /** Check-in exitoso: el QR se consume (irrepetible). */
    public void consume() {
        if (status != QrStatus.ACTIVE) {
            throw new IllegalStateException("Solo un QR ACTIVE puede consumirse (estado: " + status + ")");
        }
        this.status = QrStatus.CONSUMED;
    }

    /** Reembolso solicitado / publicación en Exchange: ACTIVE → BLOCKED (reversible). */
    public void block() {
        if (status != QrStatus.ACTIVE) {
            throw new IllegalStateException("Solo un QR ACTIVE puede bloquearse (estado: " + status + ")");
        }
        this.status = QrStatus.BLOCKED;
    }

    /** Reembolso rechazado / listing cancelado: BLOCKED → ACTIVE. */
    public void unblock() {
        if (status != QrStatus.BLOCKED) {
            throw new IllegalStateException("Solo un QR BLOCKED puede desbloquearse (estado: " + status + ")");
        }
        this.status = QrStatus.ACTIVE;
    }

    /** El boleto salió de circulación (reembolso, invalidación, transferencia). */
    public void invalidate() {
        if (status == QrStatus.CONSUMED) {
            throw new IllegalStateException("Un QR consumido no se invalida");
        }
        this.status = QrStatus.INVALIDATED;
    }

    public boolean isBlocked() {
        return status == QrStatus.BLOCKED;
    }

    public void markExpired() {
        this.status = QrStatus.EXPIRED;
    }

    public boolean belongsToTicket(UUID ticketId) {
        return this.ticketId != null && this.ticketId.equals(ticketId);
    }

    public UUID getId() {
        return id;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public QrStatus getStatus() {
        return status;
    }

    public String getKeyId() {
        return keyId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
