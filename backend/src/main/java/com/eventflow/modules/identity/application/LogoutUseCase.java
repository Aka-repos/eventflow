package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.LogoutCommand;
import com.eventflow.modules.identity.application.support.Sha256;
import com.eventflow.modules.identity.domain.port.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Logout idempotente: revoca el refresh token si existe y pertenece al usuario autenticado. */
@Service
public class LogoutUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public LogoutUseCase(RefreshTokenRepository refreshTokenRepository, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(LogoutCommand command) {
        refreshTokenRepository.findByTokenHash(Sha256.hex(command.refreshToken()))
                .filter(token -> token.getUserId().equals(command.userId()))
                .ifPresent(token -> {
                    token.revoke(clock.instant());
                    refreshTokenRepository.save(token);
                });
    }
}
