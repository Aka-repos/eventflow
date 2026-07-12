package com.eventflow.modules.ordering.domain.event;

/** Tipos/versión de los domain events de ordering (api/08 §2). */
public final class OrderEvents {

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final int VERSION = 1;

    private OrderEvents() {
    }
}
