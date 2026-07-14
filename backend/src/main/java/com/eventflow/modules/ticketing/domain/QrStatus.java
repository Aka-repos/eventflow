package com.eventflow.modules.ticketing.domain;

/** Estados del QR dinámico (DDL dynamic_qrs). Solo ACTIVE es presentable/validable. */
public enum QrStatus {
    ACTIVE, BLOCKED, INVALIDATED, CONSUMED, EXPIRED
}
