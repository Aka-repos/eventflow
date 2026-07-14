package com.eventflow.modules.refunds.domain;

/** Estados del reembolso (design/04; DDL refund_requests). CANCELLED reservado (no expuesto en M5). */
public enum RefundStatus {
    REQUESTED, APPROVED, REJECTED, CANCELLED
}
