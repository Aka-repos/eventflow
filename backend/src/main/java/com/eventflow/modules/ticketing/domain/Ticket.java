package com.eventflow.modules.ticketing.domain;

import com.eventflow.shared.domain.Money;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Agregado Ticket: ID permanente, solo cambia el propietario (design/04). policy_snapshot (ADR-03)
 * congela las condiciones vigentes al comprar; acquisition_price es la base del reembolso (C2/ADR-19).
 */
@Entity
@Table(name = "tickets", schema = "ticketing")
@SQLRestriction("deleted_at IS NULL")
public class Ticket {

    @Id
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false, updatable = false)
    private UUID ticketTypeId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "current_owner_id", nullable = false)
    private UUID currentOwnerId;

    @Column(name = "source_order_item_id", nullable = false, updatable = false)
    private UUID sourceOrderItemId;

    @Column(name = "acquisition_order_item_id", nullable = false)
    private UUID acquisitionOrderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acquired_via", nullable = false)
    private AcquiredVia acquiredVia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "original_price", nullable = false, updatable = false)
    private BigDecimal originalPrice;

    @Column(name = "acquisition_price", nullable = false)
    private BigDecimal acquisitionPrice;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, updatable = false)
    private Map<String, Object> policySnapshot;

    @Column(name = "purchased_at", nullable = false, updatable = false)
    private Instant purchasedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected Ticket() {
    }

    private Ticket(UUID id, UUID ticketTypeId, UUID eventId, UUID ownerId, UUID orderItemId,
                   Money price, Map<String, Object> policySnapshot, Instant purchasedAt) {
        this.id = id;
        this.ticketTypeId = ticketTypeId;
        this.eventId = eventId;
        this.currentOwnerId = ownerId;
        this.sourceOrderItemId = orderItemId;
        this.acquisitionOrderItemId = orderItemId;
        this.acquiredVia = AcquiredVia.PRIMARY;
        this.status = TicketStatus.ACTIVE;
        this.originalPrice = price.amount();
        this.acquisitionPrice = price.amount();
        this.currency = price.currency();
        this.policySnapshot = Map.copyOf(policySnapshot);
        this.purchasedAt = purchasedAt;
    }

    /** Emisión primaria: dueño = comprador, snapshot congelado, adquisición = original (C2). */
    public static Ticket issuePrimary(UUID ticketTypeId, UUID eventId, UUID ownerId, UUID orderItemId,
                                      Money price, Map<String, Object> policySnapshot, Instant purchasedAt) {
        return new Ticket(Uuids.v7(), ticketTypeId, eventId, ownerId, orderItemId, price,
                policySnapshot, purchasedAt);
    }

    /** Momento desde el cual el QR será visible, derivado del snapshot (ADR-03). */
    public Instant qrAvailableAt() {
        Object starts = policySnapshot.get("eventStartsAt");
        Object hours = policySnapshot.get("qrVisibilityHoursBefore");
        if (starts == null || hours == null) {
            return null;
        }
        return Instant.parse(starts.toString()).minusSeconds(((Number) hours).longValue() * 3600);
    }

    /** canRecover del contrato: ACTIVE y (ventana de reembolso abierta ∨ exchange habilitado). */
    public boolean canRecover(Instant now) {
        if (status != TicketStatus.ACTIVE) {
            return false;
        }
        Object refundEnds = policySnapshot.get("refundWindowEndsAt");
        boolean refundOpen = refundEnds != null && now.isBefore(Instant.parse(refundEnds.toString()));
        boolean exchange = Boolean.TRUE.equals(policySnapshot.get("exchangeEnabled"));
        return refundOpen || exchange;
    }

    public boolean isOwnedBy(UUID userId) {
        return currentOwnerId.equals(userId);
    }

    /** El boleto es presentable/validable solo en ACTIVE (check-in y emisión de QR). */
    public boolean isCheckInEligible() {
        return status == TicketStatus.ACTIVE;
    }

    /** Check-in exitoso: ACTIVE → USED (irreversible; el índice único de check-ins lo respalda). */
    public void markUsed() {
        if (status != TicketStatus.ACTIVE) {
            throw new com.eventflow.modules.ticketing.domain.exception.TicketBlockedException(
                    "El boleto no está activo (estado: " + status + ")");
        }
        this.status = TicketStatus.USED;
    }

    /** Solicitud de reembolso (ADR-19): ACTIVE → REFUND_PENDING. El QR se bloquea aparte. */
    public void requestRefund() {
        if (status != TicketStatus.ACTIVE) {
            throw new com.eventflow.modules.ticketing.domain.exception.TicketBlockedException(
                    "Solo un boleto ACTIVE admite reembolso (estado: " + status + ")");
        }
        this.status = TicketStatus.REFUND_PENDING;
    }

    /** Reembolso aprobado: REFUND_PENDING → REFUNDED (terminal). El QR se invalida aparte. */
    public void approveRefund() {
        requireRefundPending();
        this.status = TicketStatus.REFUNDED;
    }

    /** Reembolso rechazado: REFUND_PENDING → ACTIVE. El QR se desbloquea aparte. */
    public void rejectRefund() {
        requireRefundPending();
        this.status = TicketStatus.ACTIVE;
    }

    private void requireRefundPending() {
        if (status != TicketStatus.REFUND_PENDING) {
            throw new com.eventflow.modules.ticketing.domain.exception.TicketBlockedException(
                    "El boleto no tiene un reembolso pendiente (estado: " + status + ")");
        }
    }

    /** Invalidación por el organizador: cualquier estado no terminal → INVALIDATED. */
    public void invalidate() {
        if (status == TicketStatus.USED || status == TicketStatus.REFUNDED
                || status == TicketStatus.CANCELLED) {
            throw new com.eventflow.modules.ticketing.domain.exception.TicketBlockedException(
                    "El boleto está en un estado terminal (estado: " + status + ")");
        }
        this.status = TicketStatus.INVALIDATED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCurrentOwnerId() {
        return currentOwnerId;
    }

    public UUID getAcquisitionOrderItemId() {
        return acquisitionOrderItemId;
    }

    public AcquiredVia getAcquiredVia() {
        return acquiredVia;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public Money getOriginalPrice() {
        return new Money(originalPrice, currency);
    }

    public Money getAcquisitionPrice() {
        return new Money(acquisitionPrice, currency);
    }

    public Map<String, Object> getPolicySnapshot() {
        return policySnapshot;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }
}
