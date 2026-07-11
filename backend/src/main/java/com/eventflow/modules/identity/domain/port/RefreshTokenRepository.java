package com.eventflow.modules.identity.domain.port;

import com.eventflow.modules.identity.domain.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca todos los tokens activos del usuario (respuesta a reuso detectado). */
    void revokeAllActiveForUser(UUID userId, Instant now);
}
