package com.eventflow.modules.ticketing.domain;

import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Emisión primaria del boleto: snapshot de política (ADR-03), precios C2 y qrAvailableAt derivado. */
class TicketTest {

    private static final Instant PURCHASED = Instant.parse("2027-01-01T12:00:00Z");
    private static final Instant EVENT_STARTS = Instant.parse("2027-02-01T20:00:00Z");

    private Ticket issued() {
        return Ticket.issuePrimary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Money.of("45.00", "USD"),
                Map.of("qrVisibilityHoursBefore", 24, "refundPct", 100, "exchangeEnabled", false,
                        "eventStartsAt", EVENT_STARTS.toString()),
                PURCHASED);
    }

    @Test
    void primary_issue_sets_owner_prices_and_snapshot() {
        Ticket ticket = issued();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);
        assertThat(ticket.getAcquiredVia()).isEqualTo(AcquiredVia.PRIMARY);
        // C2: en compra primaria, precio de adquisición = precio original
        assertThat(ticket.getAcquisitionPrice()).isEqualTo(ticket.getOriginalPrice());
        assertThat(ticket.getPolicySnapshot()).containsEntry("refundPct", 100);
        assertThat(ticket.getId().version()).isEqualTo(7);
    }

    @Test
    void qr_available_at_derives_from_snapshot() {
        Ticket ticket = issued();
        assertThat(ticket.qrAvailableAt()).isEqualTo(EVENT_STARTS.minusSeconds(24 * 3600));
    }

    @Test
    void qr_available_at_is_null_without_event_start_in_snapshot() {
        Ticket ticket = Ticket.issuePrimary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Money.of("10.00", "USD"), Map.of("qrVisibilityHoursBefore", 24), PURCHASED);
        assertThat(ticket.qrAvailableAt()).isNull();
    }

    @Test
    void can_recover_only_when_active_and_policy_allows() {
        Ticket ticket = issued();
        // refund window sin definir y exchange OFF ⇒ no recuperable
        assertThat(ticket.canRecover(PURCHASED)).isFalse();

        Ticket recoverable = Ticket.issuePrimary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Money.of("10.00", "USD"),
                Map.of("exchangeEnabled", true, "eventStartsAt", EVENT_STARTS.toString()), PURCHASED);
        assertThat(recoverable.canRecover(PURCHASED)).isTrue();

        Ticket refundWindow = Ticket.issuePrimary(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Money.of("10.00", "USD"),
                Map.of("refundWindowEndsAt", PURCHASED.plusSeconds(3600).toString()), PURCHASED);
        assertThat(refundWindow.canRecover(PURCHASED)).isTrue();
        assertThat(refundWindow.canRecover(PURCHASED.plusSeconds(7200))).isFalse();
    }

    @Test
    void request_refund_moves_active_to_refund_pending() {
        Ticket ticket = issued();
        ticket.requestRefund();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.REFUND_PENDING);
    }

    @Test
    void approve_refund_moves_pending_to_refunded() {
        Ticket ticket = issued();
        ticket.requestRefund();
        ticket.approveRefund();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.REFUNDED);
    }

    @Test
    void reject_refund_returns_to_active() {
        Ticket ticket = issued();
        ticket.requestRefund();
        ticket.rejectRefund();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ACTIVE);
    }

    @Test
    void request_refund_requires_active() {
        Ticket ticket = issued();
        ticket.requestRefund();
        assertThatThrownBy(ticket::requestRefund)
                .isInstanceOf(com.eventflow.modules.ticketing.domain.exception.TicketBlockedException.class);
    }

    @Test
    void refund_transitions_require_pending() {
        Ticket ticket = issued();
        assertThatThrownBy(ticket::approveRefund)
                .isInstanceOf(com.eventflow.modules.ticketing.domain.exception.TicketBlockedException.class);
        assertThatThrownBy(ticket::rejectRefund)
                .isInstanceOf(com.eventflow.modules.ticketing.domain.exception.TicketBlockedException.class);
    }
}
