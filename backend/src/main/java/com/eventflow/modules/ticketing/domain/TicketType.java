package com.eventflow.modules.ticketing.domain;

import com.eventflow.modules.ticketing.domain.exception.TicketTypeHasSalesException;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tarifa/tipo de boleto (ticketing). Con vendidos > 0 solo se admiten cambios seguros:
 * descripción, ventana de venta y AUMENTO de cupo — precio/zona/nombre quedan congelados
 * (los compradores existentes pagaron sobre esas condiciones).
 */
@Entity
@Table(name = "ticket_types", schema = "ticketing")
public class TicketType {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "sales_starts_at")
    private Instant salesStartsAt;

    @Column(name = "sales_ends_at")
    private Instant salesEndsAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected TicketType() {
    }

    private TicketType(UUID id, UUID eventId, String name, String description, Money price,
                       UUID zoneId, int totalQuantity, Instant salesStartsAt, Instant salesEndsAt) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.description = description;
        this.price = price.amount();
        this.currency = price.currency();
        this.zoneId = zoneId;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = 0;
        this.salesStartsAt = salesStartsAt;
        this.salesEndsAt = salesEndsAt;
    }

    public static TicketType create(UUID eventId, String name, String description, Money price,
                                    UUID zoneId, int totalQuantity, Instant salesStartsAt, Instant salesEndsAt) {
        requireValid(name, totalQuantity, salesStartsAt, salesEndsAt);
        return new TicketType(Uuids.v7(), eventId, name, description, price, zoneId,
                totalQuantity, salesStartsAt, salesEndsAt);
    }

    public void update(String name, String description, Money price, UUID zoneId,
                       int totalQuantity, Instant salesStartsAt, Instant salesEndsAt) {
        requireValid(name, totalQuantity, salesStartsAt, salesEndsAt);
        if (soldQuantity > 0) {
            boolean priceChanged = price.amount().compareTo(this.price) != 0
                    || !price.currency().equals(this.currency);
            boolean zoneChanged = !Objects.equals(zoneId, this.zoneId);
            boolean nameChanged = !name.equals(this.name);
            boolean quantityReduced = totalQuantity < soldQuantity;
            if (priceChanged || zoneChanged || nameChanged || quantityReduced) {
                throw new TicketTypeHasSalesException();
            }
        }
        this.name = name;
        this.description = description;
        this.price = price.amount();
        this.currency = price.currency();
        this.zoneId = zoneId;
        this.totalQuantity = totalQuantity;
        this.salesStartsAt = salesStartsAt;
        this.salesEndsAt = salesEndsAt;
    }

    /** Guard de DELETE: solo sin ventas. */
    public void ensureDeletable() {
        if (soldQuantity > 0) {
            throw new TicketTypeHasSalesException();
        }
    }

    /**
     * Reserva cupo al crear la orden (S2: decremento bajo FOR UPDATE). Valida ventana de venta
     * y stock; el CHECK sold BETWEEN 0 AND total hace la sobreventa físicamente imposible.
     */
    public void reserve(int quantity, java.time.Instant now) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity debe ser ≥ 1");
        }
        boolean windowOpen = (salesStartsAt == null || !now.isBefore(salesStartsAt))
                && (salesEndsAt == null || now.isBefore(salesEndsAt));
        if (!windowOpen) {
            throw new com.eventflow.modules.ticketing.domain.exception.TariffSalesWindowClosedException(name);
        }
        if (soldQuantity + quantity > totalQuantity) {
            throw new com.eventflow.modules.ticketing.domain.exception.TariffSoldOutException(name);
        }
        this.soldQuantity += quantity;
    }

    /** Devuelve cupo al liberar una orden (cancelación/expiración/pago rechazado). */
    public void release(int quantity) {
        if (quantity < 1 || soldQuantity - quantity < 0) {
            throw new IllegalArgumentException("release inválido: sold=" + soldQuantity + " qty=" + quantity);
        }
        this.soldQuantity -= quantity;
    }

    private static void requireValid(String name, int totalQuantity, Instant salesStartsAt, Instant salesEndsAt) {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name es obligatorio (máx 120)");
        }
        if (totalQuantity < 1) {
            throw new IllegalArgumentException("totalQuantity debe ser ≥ 1");
        }
        if (salesStartsAt != null && salesEndsAt != null && !salesEndsAt.isAfter(salesStartsAt)) {
            throw new IllegalArgumentException("salesEndsAt debe ser posterior a salesStartsAt");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getPrice() {
        return new Money(price, currency);
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getSoldQuantity() {
        return soldQuantity;
    }

    public Instant getSalesStartsAt() {
        return salesStartsAt;
    }

    public Instant getSalesEndsAt() {
        return salesEndsAt;
    }

    public int getVersion() {
        return version;
    }
}
