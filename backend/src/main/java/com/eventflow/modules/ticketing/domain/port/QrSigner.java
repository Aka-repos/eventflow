package com.eventflow.modules.ticketing.domain.port;

import java.util.UUID;

/**
 * Puerto de firma del QR (ADR-08: JWS ES256). El token solo contiene qr_id, kid y exp —
 * jamás datos del boleto o del usuario. La verificación devuelve el qr_id si la firma y el
 * exp son válidos; cualquier manipulación (firma rota, alg cambiado, exp vencido) es un rechazo.
 */
public interface QrSigner {

    /** kid de la llave de firma vigente (se persiste en dynamic_qrs.key_id para rotación). */
    String currentKeyId();

    /** Firma un JWS ES256 opaco con {qr_id, kid, exp}. */
    String sign(UUID qrId, java.time.Instant expiresAt);

    /**
     * Verifica firma + exp y extrae el qr_id. El resultado distingue el motivo del rechazo
     * (firma/estructura inválida vs. expirado) para mapear a qr_invalid (422) o qr_expired (422).
     */
    Verification verify(String token);

    sealed interface Verification permits Verification.Valid, Verification.Invalid, Verification.Expired {

        record Valid(UUID qrId, String keyId) implements Verification {
        }

        record Invalid(String reason) implements Verification {
        }

        record Expired() implements Verification {
        }
    }
}
