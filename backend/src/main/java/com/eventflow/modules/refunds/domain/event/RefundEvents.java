package com.eventflow.modules.refunds.domain.event;

/** Domain events de reembolso (api/08 §2). TicketReleased es el integrador (S5) que consume waitlist. */
public final class RefundEvents {

    public static final String REFUND_REQUESTED = "RefundRequested";
    public static final String REFUND_APPROVED = "RefundApproved";
    public static final String REFUND_REJECTED = "RefundRejected";
    public static final String TICKET_REFUNDED = "TicketRefunded";
    public static final String TICKET_RELEASED = "TicketReleased";
    public static final int VERSION = 1;

    private RefundEvents() {
    }
}
