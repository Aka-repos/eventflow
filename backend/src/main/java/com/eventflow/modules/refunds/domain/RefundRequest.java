package com.eventflow.modules.refunds.domain;

import com.eventflow.modules.refunds.domain.exception.RefundNotPendingException;
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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado RefundRequest (refunds). El expediente se autocontiene (ADR-19): congela amount =
 * acquisition_price y referencia el payment_id a devolver. Soft delete (ADR-16). El índice único
 * parcial uq_refunds_ticket_active garantiza un solo REQUESTED por boleto a nivel de base de datos.
 */
@Entity
@Table(name = "refund_requests", schema = "commerce")
@SQLRestriction("deleted_at IS NULL")
public class RefundRequest {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private UUID requesterId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column
    private String reason;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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

    protected RefundRequest() {
    }

    private RefundRequest(UUID id, UUID ticketId, UUID requesterId, UUID paymentId, Money amount,
                          String reason) {
        this.id = id;
        this.ticketId = ticketId;
        this.requesterId = requesterId;
        this.paymentId = paymentId;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.status = RefundStatus.REQUESTED;
        this.reason = reason;
    }

    public static RefundRequest open(UUID ticketId, UUID requesterId, UUID paymentId, Money amount,
                                     String reason) {
        return new RefundRequest(Uuids.v7(), ticketId, requesterId, paymentId, amount, reason);
    }

    public void approve(UUID resolverId, Instant now) {
        requirePending();
        this.status = RefundStatus.APPROVED;
        this.resolvedBy = resolverId;
        this.resolvedAt = now;
    }

    public void reject(UUID resolverId, String reason, Instant now) {
        requirePending();
        this.status = RefundStatus.REJECTED;
        this.resolvedBy = resolverId;
        this.reason = reason;
        this.resolvedAt = now;
    }

    private void requirePending() {
        if (status != RefundStatus.REQUESTED) {
            throw new RefundNotPendingException(status.name());
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
