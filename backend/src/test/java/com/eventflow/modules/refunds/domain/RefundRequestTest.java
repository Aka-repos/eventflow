package com.eventflow.modules.refunds.domain;

import com.eventflow.modules.refunds.domain.exception.RefundNotPendingException;
import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Máquina de estados del reembolso: REQUESTED → APPROVED | REJECTED (terminal). */
class RefundRequestTest {

    private static final Instant NOW = Instant.parse("2027-03-01T12:00:00Z");

    private RefundRequest requested() {
        return RefundRequest.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.of("45.00", "USD"), "No podré asistir");
    }

    @Test
    void open_starts_in_requested_with_frozen_amount() {
        RefundRequest r = requested();
        assertThat(r.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(r.getAmount()).isEqualTo(Money.of("45.00", "USD"));
        assertThat(r.getId().version()).isEqualTo(7);
        assertThat(r.getResolvedBy()).isNull();
    }

    @Test
    void approve_from_requested_sets_resolver_and_timestamp() {
        RefundRequest r = requested();
        UUID organizer = UUID.randomUUID();
        r.approve(organizer, NOW);
        assertThat(r.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(r.getResolvedBy()).isEqualTo(organizer);
        assertThat(r.getResolvedAt()).isEqualTo(NOW);
    }

    @Test
    void reject_from_requested_requires_reason() {
        RefundRequest r = requested();
        UUID organizer = UUID.randomUUID();
        r.reject(organizer, "El evento no admite reembolsos manuales", NOW);
        assertThat(r.getStatus()).isEqualTo(RefundStatus.REJECTED);
        assertThat(r.getResolvedBy()).isEqualTo(organizer);
    }

    @Test
    void approve_only_from_requested() {
        RefundRequest r = requested();
        r.approve(UUID.randomUUID(), NOW);
        assertThatThrownBy(() -> r.approve(UUID.randomUUID(), NOW))
                .isInstanceOf(RefundNotPendingException.class);
        assertThatThrownBy(() -> r.reject(UUID.randomUUID(), "x", NOW))
                .isInstanceOf(RefundNotPendingException.class);
    }

    @Test
    void reject_only_from_requested() {
        RefundRequest r = requested();
        r.reject(UUID.randomUUID(), "motivo", NOW);
        assertThatThrownBy(() -> r.approve(UUID.randomUUID(), NOW))
                .isInstanceOf(RefundNotPendingException.class);
    }
}
