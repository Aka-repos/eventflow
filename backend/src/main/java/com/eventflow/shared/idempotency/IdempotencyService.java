package com.eventflow.shared.idempotency;

import com.eventflow.shared.config.PlatformConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotencia por clave del cliente (ADR-07, ops.idempotency_keys, scope por usuario):
 * 1) La clave se RESERVA en una TX propia (REQUIRES_NEW) antes de ejecutar la operación —
 *    un duplicado concurrente espera el commit de la primera y luego recibe la respuesta cacheada.
 * 2) Misma clave + hash distinto ⇒ 422 idempotency_key_reuse.
 * 3) Respuesta exitosa se cachea (TTL 48h por config); el reintento devuelve el MISMO cuerpo.
 * 4) Si la operación falla, la clave se libera: el cliente puede reintentar con la misma clave
 *    (la unicidad financiera la garantizan uq_orders_idem_key y uq_payments_order_settled).
 */
@Component
public class IdempotencyService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;
    private final PlatformConfig config;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                              PlatformTransactionManager txManager, PlatformConfig config, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.config = config;
        this.clock = clock;
    }

    public <T> T execute(UUID userId, UUID key, String endpoint, Object requestBody,
                         Class<T> responseType, Supplier<T> operation) {
        if (key == null) {
            throw new IdempotencyKeyRequiredException();
        }
        String hash = hash(endpoint, requestBody);
        boolean reserved = tryReserve(userId, key, endpoint, hash);
        if (!reserved) {
            return replay(userId, key, hash, responseType);
        }
        try {
            T response = operation.get();
            storeResponse(userId, key, response);
            return response;
        } catch (RuntimeException ex) {
            releaseKey(userId, key);
            throw ex;
        }
    }

    private boolean tryReserve(UUID userId, UUID key, String endpoint, String hash) {
        try {
            int hours = config.intValue("idempotency.ttl_hours", "hours", 48);
            return Boolean.TRUE.equals(requiresNew.execute(status -> {
                int inserted = jdbc.update("""
                        INSERT INTO ops.idempotency_keys (user_id, idem_key, endpoint, request_hash, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """, userId, key, endpoint, hash, java.sql.Timestamp.from(
                        clock.instant().plus(hours, ChronoUnit.HOURS)));
                return inserted == 1;
            }));
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private <T> T replay(UUID userId, UUID key, String hash, Class<T> responseType) {
        var row = jdbc.queryForMap(
                "SELECT request_hash, response_body FROM ops.idempotency_keys WHERE user_id = ? AND idem_key = ?",
                userId, key);
        if (!hash.equals(row.get("request_hash"))) {
            throw new IdempotencyKeyReuseException("La clave ya fue usada con un cuerpo distinto");
        }
        Object body = row.get("response_body");
        if (body == null) {
            throw new IdempotencyKeyReuseException("La operación original con esta clave sigue en curso");
        }
        try {
            return objectMapper.readValue(body.toString(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Respuesta idempotente cacheada ilegible", e);
        }
    }

    private void storeResponse(UUID userId, UUID key, Object response) {
        requiresNew.executeWithoutResult(status -> {
            try {
                jdbc.update("UPDATE ops.idempotency_keys SET response_status = 200, response_body = ?::jsonb "
                                + "WHERE user_id = ? AND idem_key = ?",
                        objectMapper.writeValueAsString(response), userId, key);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("No se pudo serializar la respuesta idempotente", e);
            }
        });
    }

    private void releaseKey(UUID userId, UUID key) {
        requiresNew.executeWithoutResult(status ->
                jdbc.update("DELETE FROM ops.idempotency_keys WHERE user_id = ? AND idem_key = ?", userId, key));
    }

    private String hash(String endpoint, Object requestBody) {
        try {
            String canonical = endpoint + "|" + (requestBody == null ? "" : objectMapper.writeValueAsString(requestBody));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("No se pudo calcular el hash de idempotencia", e);
        }
    }
}
