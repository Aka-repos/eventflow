package com.eventflow.modules.identity.domain;

import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token con rotación (ADR de auth): al usarse se invalida y encadena a su sucesor.
 * Un token revocado CON sucesor que vuelve a presentarse = señal de robo (se revoca la familia).
 * Solo se persiste el hash SHA-256, jamás el token en claro.
 */
@Entity
@Table(name = "refresh_tokens", schema = "identity")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(UUID userId, String tokenHash, Instant now, Duration ttl) {
        return new RefreshToken(Uuids.v7(), userId, tokenHash, now.plus(ttl));
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void markReplacedBy(UUID successorId, Instant now) {
        revoke(now);
        this.replacedBy = successorId;
    }

    public boolean wasReplaced() {
        return replacedBy != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }
}
