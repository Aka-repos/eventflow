package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.RefreshCommand;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.application.support.Sha256;
import com.eventflow.modules.identity.domain.RefreshToken;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.InvalidRefreshTokenException;
import com.eventflow.modules.identity.domain.exception.RefreshTokenReusedException;
import com.eventflow.modules.identity.domain.port.RefreshTokenRepository;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Rotación de refresh tokens con detección de robo: un token ya rotado que vuelve a
 * presentarse revoca TODA la familia activa del usuario (fuerza re-login).
 */
@Service
public class RefreshTokenUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final Clock clock;

    public RefreshTokenUseCase(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
                               AuthTokenIssuer tokenIssuer, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Transactional
    public AuthResult execute(RefreshCommand command) {
        Instant now = clock.instant();
        RefreshToken current = refreshTokenRepository.findByTokenHash(Sha256.hex(command.refreshToken()))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!current.isActive(now)) {
            if (current.wasReplaced()) {
                refreshTokenRepository.revokeAllActiveForUser(current.getUserId(), now);
                throw new RefreshTokenReusedException();
            }
            throw new InvalidRefreshTokenException();
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        user.ensureCanAuthenticate();

        AuthResult result = tokenIssuer.issueFor(user).withUser(user);
        current.markReplacedBy(result.refreshTokenId(), now);
        refreshTokenRepository.save(current);
        return result;
    }
}
