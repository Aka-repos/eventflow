package com.eventflow.modules.ticketing.infrastructure.security;

import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Provee el material de firma del QR. Si hay llaves EC configuradas (base64 PKCS8/X509) las usa;
 * en su ausencia (dev/test) genera un par ES256 efímero — arranque sin secretos, pero los QR no
 * sobreviven a un reinicio (aceptable en dev; en prod se inyectan las llaves reales).
 */
@Configuration
class QrKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(QrKeyConfig.class);

    @Bean
    QrKeyMaterial qrKeyMaterial(
            @Value("${eventflow.qr.ec-private-key:}") String privateKeyB64,
            @Value("${eventflow.qr.ec-public-key:}") String publicKeyB64,
            @Value("${eventflow.qr.key-id:}") String configuredKid) {
        if (!privateKeyB64.isBlank() && !publicKeyB64.isBlank()) {
            KeyPair pair = loadEcKeyPair(privateKeyB64, publicKeyB64);
            String kid = configuredKid.isBlank() ? deriveKid(publicKeyB64) : configuredKid;
            log.info("qr_signing_key loaded from config kid={}", kid);
            return new QrKeyMaterial(kid, pair);
        }
        KeyPair pair = Jwts.SIG.ES256.keyPair().build();
        String kid = "dev-" + deriveKid(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        log.warn("qr_signing_key generated ephemerally (dev only) kid={} — configure eventflow.qr.* in prod", kid);
        return new QrKeyMaterial(kid, pair);
    }

    private KeyPair loadEcKeyPair(String privateKeyB64, String publicKeyB64) {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            var priv = kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyB64)));
            var pub = kf.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64)));
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron cargar las llaves EC del QR (eventflow.qr.*)", e);
        }
    }

    /** kid corto y estable derivado de la pública (primeros bytes del SHA-256). */
    private String deriveKid(String publicKeyB64) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(publicKeyB64.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
