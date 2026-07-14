package com.eventflow.modules.ticketing.domain;

import com.eventflow.modules.ticketing.domain.RecoveryPolicy.RecoverySubject;
import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla ADR-19 de cancelación inteligente (determinista). El precio del exchange lo calcula el server. */
class RecoveryPolicyTest {

    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");

    private RecoverySubject primary(Map<String, Object> snapshot) {
        return new RecoverySubject(AcquiredVia.PRIMARY, TicketStatus.ACTIVE,
                Money.of("80.00", "USD"), Money.of("80.00", "USD"), snapshot);
    }

    private RecoverySubject fromExchange(Map<String, Object> snapshot) {
        return new RecoverySubject(AcquiredVia.EXCHANGE, TicketStatus.ACTIVE,
                Money.of("80.00", "USD"), Money.of("72.00", "USD"), snapshot);
    }

    @Test
    void primary_with_active_refund_window_offers_refund_100pct() {
        var r = RecoveryPolicy.evaluate(primary(
                Map.of("refundWindowEndsAt", NOW.plusSeconds(3600).toString(), "refundPct", 100)), NOW);
        assertThat(r.option()).isEqualTo(RecoveryPolicy.Option.REFUND);
        assertThat(r.reason()).isEqualTo(RecoveryPolicy.Reason.REFUND_WINDOW_ACTIVE);
        assertThat(r.refundAmount()).isEqualTo(Money.of("80.00", "USD")); // acquisitionPrice
    }

    @Test
    void primary_expired_window_but_exchange_enabled_offers_exchange_with_quote() {
        var r = RecoveryPolicy.evaluate(primary(Map.of(
                "refundWindowEndsAt", NOW.minusSeconds(60).toString(),
                "exchangeEnabled", true, "exchangeDepreciationPct", 10)), NOW);
        assertThat(r.option()).isEqualTo(RecoveryPolicy.Option.EXCHANGE);
        assertThat(r.reason()).isEqualTo(RecoveryPolicy.Reason.REFUND_WINDOW_EXPIRED);
        // listPrice = originalPrice × (1 − 10%) con redondeo M1
        assertThat(r.exchangeListPrice()).isEqualTo(Money.of("72.00", "USD"));
        assertThat(r.exchangeDepreciationPct()).isEqualTo(10);
    }

    @Test
    void primary_expired_window_no_exchange_offers_none() {
        var r = RecoveryPolicy.evaluate(primary(Map.of(
                "refundWindowEndsAt", NOW.minusSeconds(60).toString(), "exchangeEnabled", false)), NOW);
        assertThat(r.option()).isEqualTo(RecoveryPolicy.Option.NONE);
        assertThat(r.reason()).isEqualTo(RecoveryPolicy.Reason.EXCHANGE_DISABLED);
    }

    @Test
    void exchange_acquired_never_refund_even_with_open_window() {
        // ADR-19: EXCHANGE jamás REFUND, aunque la ventana esté abierta
        var r = RecoveryPolicy.evaluate(fromExchange(Map.of(
                "refundWindowEndsAt", NOW.plusSeconds(3600).toString(),
                "exchangeEnabled", true, "exchangeDepreciationPct", 10)), NOW);
        assertThat(r.option()).isEqualTo(RecoveryPolicy.Option.EXCHANGE);
        assertThat(r.reason()).isEqualTo(RecoveryPolicy.Reason.ACQUIRED_VIA_EXCHANGE);
        assertThat(r.refundAmount()).isNull();
    }

    @Test
    void exchange_acquired_without_exchange_enabled_is_none() {
        var r = RecoveryPolicy.evaluate(fromExchange(Map.of("exchangeEnabled", false)), NOW);
        assertThat(r.option()).isEqualTo(RecoveryPolicy.Option.NONE);
        assertThat(r.reason()).isEqualTo(RecoveryPolicy.Reason.ACQUIRED_VIA_EXCHANGE);
    }

    @Test
    void non_active_ticket_is_not_recoverable() {
        var subject = new RecoverySubject(AcquiredVia.PRIMARY, TicketStatus.REFUND_PENDING,
                Money.of("80.00", "USD"), Money.of("80.00", "USD"),
                Map.of("refundWindowEndsAt", NOW.plusSeconds(3600).toString()));
        var r = RecoveryPolicy.evaluate(subject, NOW);
        assertThat(r.option()).isEqualTo(RecoveryPolicy.Option.NONE);
        assertThat(r.reason()).isEqualTo(RecoveryPolicy.Reason.TICKET_NOT_RECOVERABLE);
    }
}
