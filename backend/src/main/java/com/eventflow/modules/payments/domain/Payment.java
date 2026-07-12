package com.eventflow.modules.payments.domain;

import com.eventflow.shared.domain.Money;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment-intent (auditoría A2): la fila PENDING se persiste ANTES de invocar al proveedor;
 * el índice único uq_payments_order_settled hace físicamente imposible cobrar dos veces (A4).
 */
@Entity
@Table(name = "payments", schema = "commerce")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_ref")
    private String providerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    private Payment(UUID id, UUID orderId, String provider, Money amount) {
        this.id = id;
        this.orderId = orderId;
        this.provider = provider;
        this.status = PaymentStatus.PENDING;
        this.amount = amount.amount();
        this.currency = amount.currency();
    }

    public static Payment intent(UUID orderId, String provider, Money amount) {
        return new Payment(Uuids.v7(), orderId, provider, amount);
    }

    public void approve(String providerRef) {
        requirePending();
        this.status = PaymentStatus.APPROVED;
        this.providerRef = providerRef;
    }

    public void decline(String reason) {
        requirePending();
        this.status = PaymentStatus.DECLINED;
        this.failureReason = reason;
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("El intento de pago ya fue resuelto: " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getProvider() {
        return provider;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }

    public String getFailureReason() {
        return failureReason;
    }
}
