package com.eventflow.modules.ordering.domain;

import com.eventflow.shared.domain.Money;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ítem polimórfico cerrado (auditoría I1): exactamente una referencia acorde al tipo.
 * En el Módulo 3 solo se emite TICKET; PARKING (M8) y EXCHANGE_TICKET (M6) llegan después.
 */
@Entity
@Table(name = "order_items", schema = "commerce")
public class OrderItem {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "item_type", nullable = false, updatable = false)
    private String itemType;

    @Column(name = "ticket_type_id", updatable = false)
    private UUID ticketTypeId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false, columnDefinition = "char(3)")
    private String currency;

    @jakarta.persistence.Transient
    private String description;

    protected OrderItem() {
    }

    private OrderItem(UUID id, String itemType, UUID ticketTypeId, int quantity, Money unitPrice,
                      String description) {
        this.id = id;
        this.itemType = itemType;
        this.ticketTypeId = ticketTypeId;
        this.quantity = quantity;
        this.unitPrice = unitPrice.amount();
        this.currency = unitPrice.currency();
        this.description = description;
    }

    public static OrderItem ticket(UUID ticketTypeId, int quantity, Money unitPrice, String description) {
        if (quantity < 1 || quantity > 10) {
            throw new IllegalArgumentException("quantity debe estar entre 1 y 10");
        }
        return new OrderItem(Uuids.v7(), "TICKET", ticketTypeId, quantity, unitPrice, description);
    }

    void attachTo(UUID orderId) {
        this.orderId = orderId;
    }

    public Money lineTotal() {
        return new Money(unitPrice.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public UUID getId() {
        return id;
    }

    public String getItemType() {
        return itemType;
    }

    public UUID getTicketTypeId() {
        return ticketTypeId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return new Money(unitPrice, currency);
    }

    /** Descripción armada por el servidor al crear (p. ej. "VIP — Concierto X"); transitoria. */
    public String getDescription() {
        return description;
    }

    public void describeAs(String description) {
        this.description = description;
    }
}
