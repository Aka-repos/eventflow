package com.eventflow.modules.ledger.domain;

import com.eventflow.shared.domain.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Asiento de partida doble, append-only (ADR-14; inmutable a nivel BD por REVOKE en V8).
 * Cuentas: PLATFORM | BUYER:<uuid> | SELLER:<uuid> | ORGANIZER:<uuid>.
 */
@Entity
@Table(name = "ledger_entries", schema = "commerce")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_type", nullable = false, updatable = false)
    private String entryType;

    @Column(name = "source_account", nullable = false, updatable = false)
    private String sourceAccount;

    @Column(name = "destination_account", nullable = false, updatable = false)
    private String destinationAccount;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "fee_amount", updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "reference_type", nullable = false, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private Map<String, Object> details;

    protected LedgerEntry() {
    }

    private LedgerEntry(String entryType, String sourceAccount, String destinationAccount, Money amount,
                        Money fee, String referenceType, UUID referenceId, UUID eventId,
                        Map<String, Object> details) {
        if (sourceAccount.equals(destinationAccount)) {
            throw new IllegalArgumentException("Las cuentas origen y destino deben diferir");
        }
        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("El asiento requiere amount > 0");
        }
        this.entryType = entryType;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount.amount();
        this.currency = amount.currency();
        this.feeAmount = fee == null ? null : fee.amount();
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.eventId = eventId;
        this.details = details;
    }

    /** Venta primaria: BUYER → ORGANIZER por el subtotal del evento dentro de la orden (fee 0 en v1). */
    public static LedgerEntry sale(UUID buyerId, UUID organizerId, Money amount, UUID orderId, UUID eventId,
                                   Map<String, Object> details) {
        return new LedgerEntry("SALE", "BUYER:" + buyerId, "ORGANIZER:" + organizerId, amount,
                null, "ORDER", orderId, eventId, details);
    }

    public String getEntryType() {
        return entryType;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public Money getAmount() {
        return new Money(amount, currency);
    }
}
