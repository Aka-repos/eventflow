package com.eventflow.modules.identity.infrastructure.persistence;

import com.eventflow.modules.identity.domain.RefreshToken;
import com.eventflow.modules.identity.domain.port.RefreshTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
class JpaRefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;

    JpaRefreshTokenRepositoryAdapter(SpringDataRefreshTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return jpa.save(token);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash);
    }

    /**
     * REQUIRES_NEW deliberado: la revocación de la familia ante reuso detectado (posible robo)
     * debe COMMITEAR aunque la transacción del caso de uso haga rollback al lanzar
     * RefreshTokenReusedException. Sin esto, el rollback desharía la defensa.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(UUID userId, Instant now) {
        jpa.revokeAllActiveForUser(userId, now);
    }
}
