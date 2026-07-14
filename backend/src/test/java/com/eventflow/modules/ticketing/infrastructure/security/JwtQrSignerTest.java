package com.eventflow.modules.ticketing.infrastructure.security;

import com.eventflow.modules.ticketing.domain.port.QrSigner;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El firmante ES256 es la única barrera criptográfica del QR: firma, verifica, caduca y detecta fraude. */
class JwtQrSignerTest {

    private final QrSigner signer = new JwtQrSigner(
            new QrKeyMaterial("kid-test", Jwts.SIG.ES256.keyPair().build()));

    @Test
    void sign_then_verify_roundtrip_returns_qr_id_and_kid() {
        UUID qrId = UUID.randomUUID();
        String token = signer.sign(qrId, Instant.now().plus(1, ChronoUnit.HOURS));

        QrSigner.Verification result = signer.verify(token);

        assertThat(result).isInstanceOf(QrSigner.Verification.Valid.class);
        QrSigner.Verification.Valid valid = (QrSigner.Verification.Valid) result;
        assertThat(valid.qrId()).isEqualTo(qrId);
        assertThat(valid.keyId()).isEqualTo("kid-test");
    }

    @Test
    void expired_token_is_reported_as_expired_not_invalid() {
        String token = signer.sign(UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(signer.verify(token)).isInstanceOf(QrSigner.Verification.Expired.class);
    }

    @Test
    void token_signed_by_another_key_is_invalid() {
        // token legítimo de OTRO firmante (llave distinta) — firma no verifica con la nuestra
        QrSigner attacker = new JwtQrSigner(
                new QrKeyMaterial("kid-attacker", Jwts.SIG.ES256.keyPair().build()));
        String forged = attacker.sign(UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(signer.verify(forged)).isInstanceOf(QrSigner.Verification.Invalid.class);
    }

    @Test
    void tampered_token_is_invalid() {
        String token = signer.sign(UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.HOURS));
        // alterar el último carácter de la firma rompe la verificación
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("A") ? "B" : "A");
        assertThat(signer.verify(tampered)).isInstanceOf(QrSigner.Verification.Invalid.class);
    }

    @Test
    void garbage_and_empty_tokens_are_invalid() {
        assertThat(signer.verify("")).isInstanceOf(QrSigner.Verification.Invalid.class);
        assertThat(signer.verify("no-es-un-jws")).isInstanceOf(QrSigner.Verification.Invalid.class);
        assertThat(signer.verify(null)).isInstanceOf(QrSigner.Verification.Invalid.class);
    }
}
