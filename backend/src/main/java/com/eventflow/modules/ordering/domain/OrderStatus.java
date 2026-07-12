package com.eventflow.modules.ordering.domain;

/** Máquina de estados de la orden (design/04): PENDING → PAID | FAILED | CANCELLED; REFUNDED en M5. */
public enum OrderStatus {
    PENDING, PAID, FAILED, CANCELLED, REFUNDED
}
