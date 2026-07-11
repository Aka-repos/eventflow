package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.application.support.Sha256;
import com.eventflow.modules.identity.application.support.TokenGenerator;
import com.eventflow.modules.identity.domain.RefreshToken;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.port.RefreshTokenRepository;
import com.eventflow.shared.security.JwtProperties;
import com.eventflow.shared.security.JwtProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;

/** Emite el par access (JWT) + refresh (opaco, persistido como hash) para un usuario ya validado. */
@Component
public class AuthTokenIssuer {

    private final JwtProvider jwtProvider;
    private final TokenGenerator tokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthTokenIssuer(JwtProvider jwtProvider, TokenGenerator tokenGenerator,
                           RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties, Clock clock) {
        this.jwtProvider = jwtProvider;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    public AuthResult issueFor(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.roleCodes());
        String refreshPlain = tokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                user.getId(), Sha256.hex(refreshPlain), clock.instant(), jwtProperties.refreshTtl());
        refreshTokenRepository.save(refreshToken);
        return new AuthResult(accessToken, jwtProvider.accessTtlSeconds(), refreshPlain,
                refreshToken.getId(), user);
    }
}
