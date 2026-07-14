package com.eventflow.modules.ticketing.infrastructure.security;

import com.eventflow.modules.ticketing.domain.port.QrSigner;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Firma de QR con JWS ES256 (ADR-08). El token opaco lleva solo qr_id (subject), kid (header) y exp.
 * ES256 (ECDSA P-256) permite verificar con la pública sin exponer la privada — base para que
 * futuros validadores externos confíen sin compartir secretos. La llave y su kid provienen de la
 * configuración; en dev, QrKeyConfig genera un par efímero. Rotación: cambiar kid+llave, los QR
 * viejos siguen verificándose mientras su kid esté disponible (aquí, uno vigente para el MVP).
 */
@Component
class JwtQrSigner implements QrSigner {

    private static final Logger log = LoggerFactory.getLogger(JwtQrSigner.class);

    private final String keyId;
    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;

    JwtQrSigner(QrKeyMaterial keyMaterial) {
        this.keyId = keyMaterial.keyId();
        KeyPair pair = keyMaterial.keyPair();
        this.privateKey = (ECPrivateKey) pair.getPrivate();
        this.publicKey = (ECPublicKey) pair.getPublic();
    }

    @Override
    public String currentKeyId() {
        return keyId;
    }

    @Override
    public String sign(UUID qrId, Instant expiresAt) {
        return Jwts.builder()
                .header().keyId(keyId).and()
                .subject(qrId.toString())
                .expiration(Date.from(expiresAt))
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    @Override
    public Verification verify(String token) {
        if (token == null || token.isBlank()) {
            return new Verification.Invalid("token vacío");
        }
        try {
            var jws = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            String kid = (String) jws.getHeader().getKeyId();
            UUID qrId = UUID.fromString(jws.getPayload().getSubject());
            return new Verification.Valid(qrId, kid);
        } catch (ExpiredJwtException e) {
            return new Verification.Expired();
        } catch (JwtException | IllegalArgumentException e) {
            // firma rota, alg manipulado, estructura inválida, subject no-UUID
            log.warn("qr_verify_rejected reason={}", e.getClass().getSimpleName());
            return new Verification.Invalid(e.getClass().getSimpleName());
        }
    }
}
