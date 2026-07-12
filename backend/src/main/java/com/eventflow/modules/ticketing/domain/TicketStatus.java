package com.eventflow.modules.ticketing.domain;

/** Estados del boleto (design/04; transiciones se amplían en M4-M6). */
public enum TicketStatus {
    ACTIVE, PUBLISHED_IN_EXCHANGE, REFUND_PENDING, REFUNDED, USED, EXPIRED, CANCELLED, INVALIDATED
}
