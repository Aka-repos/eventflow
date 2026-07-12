package com.eventflow.modules.ordering.domain;

import com.eventflow.modules.ordering.domain.exception.OrderExpiredException;
import com.eventflow.modules.ordering.domain.exception.OrderNotPendingException;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agregado Order. Invariantes (07-bd-06 §9): total = Σ ítems, moneda homogénea; el estado
 * solo cambia por métodos de negocio. La ventana de pago la fija expires_at (scheduler ADR-10).
 */
@Entity
@Table(name = "orders", schema = "commerce")
@SQLRestriction("deleted_at IS NULL")
public class Order {

    @Id
    private UUID id;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "idempotency_key", updatable = false)
    private UUID idempotencyKey;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "order_id", updatable = false, insertable = false)
    private List<OrderItem> items = new ArrayList<>();

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

    protected Order() {
    }

    private Order(UUID id, UUID buyerId, UUID idempotencyKey, Instant expiresAt, List<OrderItem> items,
                  Money total) {
        this.id = id;
        this.buyerId = buyerId;
        this.idempotencyKey = idempotencyKey;
        this.expiresAt = expiresAt;
        this.status = OrderStatus.PENDING;
        this.totalAmount = total.amount();
        this.currency = total.currency();
        this.items = items;
    }

    public static Order create(UUID buyerId, UUID idempotencyKey, Instant expiresAt, List<OrderItem> items) {
        if (items == null || items.isEmpty() || items.size() > 10) {
            throw new IllegalArgumentException("La orden requiere entre 1 y 10 ítems");
        }
        Money total = items.stream().map(OrderItem::lineTotal).reduce(Money::add)
                .orElseThrow(() -> new IllegalArgumentException("La orden requiere ítems"));
        UUID orderId = Uuids.v7();
        Order order = new Order(orderId, buyerId, idempotencyKey, expiresAt, new ArrayList<>(items), total);
        items.forEach(item -> item.attachTo(orderId));
        return order;
    }

    /** Guard de /pay y /cancel: la orden debe seguir PENDING y dentro de la ventana. */
    public void ensurePayable(Instant now) {
        ensurePending();
        if (now.isAfter(expiresAt)) {
            throw new OrderExpiredException();
        }
    }

    public void ensurePending() {
        if (status != OrderStatus.PENDING) {
            throw new OrderNotPendingException(status.name());
        }
    }

    public void markPaid() {
        ensurePending();
        this.status = OrderStatus.PAID;
    }

    public void markFailed() {
        ensurePending();
        this.status = OrderStatus.FAILED;
    }

    public void cancel() {
        ensurePending();
        this.status = OrderStatus.CANCELLED;
    }

    public boolean isOwnedBy(UUID userId) {
        return buyerId.equals(userId);
    }

    public boolean isExpired(Instant now) {
        return status == OrderStatus.PENDING && now.isAfter(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Money getTotal() {
        return new Money(totalAmount, currency);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
