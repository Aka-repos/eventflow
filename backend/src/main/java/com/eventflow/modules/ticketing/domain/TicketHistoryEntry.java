package com.eventflow.modules.ticketing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Historial append-only del boleto (inmutable a nivel BD: REVOKE UPDATE/DELETE, V8). */
@Entity
@Table(name = "ticket_history", schema = "ticketing")
public class TicketHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "from_status", nullable = false, updatable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;

    @Column(nullable = false, updatable = false)
    private String cause;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected TicketHistoryEntry() {
    }

    private TicketHistoryEntry(UUID ticketId, String fromStatus, String toStatus, String cause, UUID actorId) {
        this.ticketId = ticketId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.cause = cause;
        this.actorId = actorId;
    }

    /** from_status es NOT NULL en BD: la emisión registra ACTIVE→ACTIVE con causa ISSUED. */
    public static TicketHistoryEntry issued(UUID ticketId, UUID actorId) {
        return new TicketHistoryEntry(ticketId, TicketStatus.ACTIVE.name(), TicketStatus.ACTIVE.name(),
                "ISSUED", actorId);
    }

    /** Entrada genérica del historial (causa ∈ CHECK de BD: CHECKIN, INVALIDATE, REISSUE…). */
    public static TicketHistoryEntry of(UUID ticketId, String fromStatus, String toStatus, String cause,
                                        UUID actorId) {
        return new TicketHistoryEntry(ticketId, fromStatus, toStatus, cause, actorId);
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getCause() {
        return cause;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
