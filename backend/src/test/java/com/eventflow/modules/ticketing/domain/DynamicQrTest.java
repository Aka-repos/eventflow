package com.eventflow.modules.ticketing.domain;

import com.eventflow.modules.ticketing.domain.exception.QrNotYetVisibleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del agregado DynamicQr: ventana de visibilidad, expiración, estados y consumo. */
class DynamicQrTest {

    private static final Instant NOW = Instant.parse("2027-02-01T18:00:00Z");
    private static final Instant EVENT_STARTS = Instant.parse("2027-02-01T20:00:00Z");
    private static final int VISIBILITY_HOURS = 24;
    private static final int EXPIRATION_MINUTES = 60;

    private DynamicQr issued() {
        return DynamicQr.issueForTicket(UUID.randomUUID(), "kid-2027", NOW, EXPIRATION_MINUTES);
    }

    @Test
    void issue_sets_active_with_kid_and_expiry() {
        DynamicQr qr = issued();
        assertThat(qr.getStatus()).isEqualTo(QrStatus.ACTIVE);
        assertThat(qr.getSubjectType()).isEqualTo("TICKET");
        assertThat(qr.getKeyId()).isEqualTo("kid-2027");
        assertThat(qr.getExpiresAt()).isEqualTo(NOW.plusSeconds(EXPIRATION_MINUTES * 60L));
        assertThat(qr.getId().version()).isEqualTo(7);
    }

    @Test
    void refresh_after_is_before_expiry() {
        DynamicQr qr = issued();
        // el cliente debe re-pedir el QR antes de que expire (dinámico)
        assertThat(qr.refreshAfter()).isBefore(qr.getExpiresAt());
        assertThat(qr.refreshAfter()).isAfter(NOW);
    }

    @Test
    void visibility_window_gate_before_available() {
        Instant availableAt = EVENT_STARTS.minusSeconds(VISIBILITY_HOURS * 3600L);
        // NOW (18:00) está DENTRO de la ventana (disponible desde el día anterior 20:00)
        DynamicQr.ensureVisible(availableAt, NOW);

        // un evento cuya ventana abre en el futuro rechaza la emisión
        Instant futureAvailable = NOW.plusSeconds(3600);
        assertThatThrownBy(() -> DynamicQr.ensureVisible(futureAvailable, NOW))
                .isInstanceOf(QrNotYetVisibleException.class);
    }

    @Test
    void is_valid_at_only_while_active_and_not_expired() {
        DynamicQr qr = issued();
        assertThat(qr.isValidAt(NOW.plusSeconds(60))).isTrue();
        assertThat(qr.isValidAt(qr.getExpiresAt().plusSeconds(1))).isFalse();
    }

    @Test
    void consume_transitions_active_to_consumed_once() {
        DynamicQr qr = issued();
        qr.consume();
        assertThat(qr.getStatus()).isEqualTo(QrStatus.CONSUMED);
        // no se puede consumir de nuevo (defensa en profundidad; la BD también lo impide)
        assertThatThrownBy(qr::consume).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidate_from_active() {
        DynamicQr qr = issued();
        qr.invalidate();
        assertThat(qr.getStatus()).isEqualTo(QrStatus.INVALIDATED);
        assertThat(qr.isValidAt(NOW)).isFalse();
    }

    @Test
    void expire_marks_expired_state() {
        DynamicQr qr = issued();
        qr.markExpired();
        assertThat(qr.getStatus()).isEqualTo(QrStatus.EXPIRED);
    }
}
