package com.eventflow.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Política configurable del evento (ADR-02), 1:1 con events (PK = FK). Se crea con defaults
 * al crear el evento; el organizador la reemplaza vía PUT con If-Match (optimistic lock).
 * ADR-03: cambiarla no afecta boletos ya vendidos (ellos llevan snapshot).
 */
@Entity
@Table(name = "event_policies", schema = "catalog")
public class EventPolicy {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "refund_window_ends_at")
    private Instant refundWindowEndsAt;

    @Column(name = "refund_pct", nullable = false)
    private short refundPct;

    @Column(name = "exchange_enabled", nullable = false)
    private boolean exchangeEnabled;

    @Column(name = "exchange_depreciation_pct", nullable = false)
    private short exchangeDepreciationPct;

    @Column(name = "exchange_listing_deadline")
    private Instant exchangeListingDeadline;

    @Column(name = "waitlist_enabled", nullable = false)
    private boolean waitlistEnabled;

    @Column(name = "waitlist_offer_minutes", nullable = false)
    private int waitlistOfferMinutes;

    @Column(name = "temp_reservation_minutes", nullable = false)
    private int tempReservationMinutes;

    @Column(name = "qr_visibility_hours_before", nullable = false)
    private int qrVisibilityHoursBefore;

    @Column(name = "qr_expiration_minutes", nullable = false)
    private int qrExpirationMinutes;

    @Column(name = "cancellation_policy")
    private String cancellationPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_policies", nullable = false)
    private Map<String, Object> extraPolicies;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected EventPolicy() {
    }

    private EventPolicy(UUID eventId) {
        this.eventId = eventId;
        this.refundPct = 100;
        this.exchangeEnabled = false;
        this.exchangeDepreciationPct = 10;
        this.waitlistEnabled = false;
        this.waitlistOfferMinutes = 15;
        this.tempReservationMinutes = 10;
        this.qrVisibilityHoursBefore = 24;
        this.qrExpirationMinutes = 60;
        this.extraPolicies = Map.of();
    }

    /** Defaults espejo de la migración V3 — se crea junto con el evento. */
    public static EventPolicy defaultsFor(UUID eventId) {
        return new EventPolicy(eventId);
    }

    /** PUT = reemplazo completo (el contrato exige todos los campos requeridos). */
    public void replace(Instant refundWindowEndsAt, boolean exchangeEnabled, short exchangeDepreciationPct,
                        Instant exchangeListingDeadline, boolean waitlistEnabled, int waitlistOfferMinutes,
                        int tempReservationMinutes, int qrVisibilityHoursBefore, int qrExpirationMinutes,
                        String cancellationPolicy, Map<String, Object> extraPolicies) {
        requireRange("exchangeDepreciationPct", exchangeDepreciationPct, 0, 100);
        requirePositive("waitlistOfferMinutes", waitlistOfferMinutes);
        requirePositive("tempReservationMinutes", tempReservationMinutes);
        requirePositive("qrExpirationMinutes", qrExpirationMinutes);
        if (qrVisibilityHoursBefore < 0) {
            throw new IllegalArgumentException("qrVisibilityHoursBefore no puede ser negativo");
        }
        this.refundWindowEndsAt = refundWindowEndsAt;
        this.exchangeEnabled = exchangeEnabled;
        this.exchangeDepreciationPct = exchangeDepreciationPct;
        this.exchangeListingDeadline = exchangeListingDeadline;
        this.waitlistEnabled = waitlistEnabled;
        this.waitlistOfferMinutes = waitlistOfferMinutes;
        this.tempReservationMinutes = tempReservationMinutes;
        this.qrVisibilityHoursBefore = qrVisibilityHoursBefore;
        this.qrExpirationMinutes = qrExpirationMinutes;
        this.cancellationPolicy = cancellationPolicy;
        this.extraPolicies = extraPolicies == null ? Map.of() : Map.copyOf(extraPolicies);
    }

    private static void requireRange(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " debe estar entre " + min + " y " + max);
        }
    }

    private static void requirePositive(String field, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " debe ser positivo");
        }
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getRefundWindowEndsAt() {
        return refundWindowEndsAt;
    }

    public short getRefundPct() {
        return refundPct;
    }

    public boolean isExchangeEnabled() {
        return exchangeEnabled;
    }

    public short getExchangeDepreciationPct() {
        return exchangeDepreciationPct;
    }

    public Instant getExchangeListingDeadline() {
        return exchangeListingDeadline;
    }

    public boolean isWaitlistEnabled() {
        return waitlistEnabled;
    }

    public int getWaitlistOfferMinutes() {
        return waitlistOfferMinutes;
    }

    public int getTempReservationMinutes() {
        return tempReservationMinutes;
    }

    public int getQrVisibilityHoursBefore() {
        return qrVisibilityHoursBefore;
    }

    public int getQrExpirationMinutes() {
        return qrExpirationMinutes;
    }

    public String getCancellationPolicy() {
        return cancellationPolicy;
    }

    public Map<String, Object> getExtraPolicies() {
        return extraPolicies;
    }

    public int getVersion() {
        return version;
    }
}
