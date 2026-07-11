package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.RefreshCommand;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.application.support.Sha256;
import com.eventflow.modules.identity.domain.RefreshToken;
import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.InvalidRefreshTokenException;
import com.eventflow.modules.identity.domain.exception.RefreshTokenReusedException;
import com.eventflow.modules.identity.domain.port.RefreshTokenRepository;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final String PLAIN = "refresh-plain-token";
    private static final String HASH = Sha256.hex(PLAIN);

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock UserRepository userRepository;
    @Mock AuthTokenIssuer tokenIssuer;

    private RefreshTokenUseCase useCase() {
        return new RefreshTokenUseCase(refreshTokenRepository, userRepository, tokenIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User user() {
        return User.register("ana@mail.com", "$2a$hash", "Ana P.", null, Role.of(4, RoleCode.ATTENDEE));
    }

    @Test
    void should_rotate_token_and_chain_replacement_when_active() {
        // Given
        User user = user();
        RefreshToken current = RefreshToken.issue(user.getId(), HASH, NOW.minus(Duration.ofDays(1)), Duration.ofDays(14));
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(current));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        UUID newTokenId = UUID.randomUUID();
        when(tokenIssuer.issueFor(user)).thenReturn(new AuthResult("access", 900, "new-refresh", newTokenId, user));

        // When
        AuthResult result = useCase().execute(new RefreshCommand(PLAIN));

        // Then: rotación — el token usado queda revocado y encadenado a su sucesor
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(current.isActive(NOW)).isFalse();
        assertThat(current.getReplacedBy()).isEqualTo(newTokenId);
        verify(refreshTokenRepository).save(current);
    }

    @Test
    void should_reject_unknown_token() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(new RefreshCommand("desconocido")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void should_reject_expired_token_without_revoking_family() {
        RefreshToken expired = RefreshToken.issue(UUID.randomUUID(), HASH,
                NOW.minus(Duration.ofDays(30)), Duration.ofDays(14));
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> useCase().execute(new RefreshCommand(PLAIN)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }

    @Test
    void should_detect_reuse_of_rotated_token_and_revoke_family() {
        // Given: token ya rotado (revocado con sucesor) que vuelve a presentarse = robo
        UUID userId = UUID.randomUUID();
        RefreshToken rotated = RefreshToken.issue(userId, HASH, NOW.minus(Duration.ofDays(1)), Duration.ofDays(14));
        rotated.markReplacedBy(UUID.randomUUID(), NOW.minus(Duration.ofHours(1)));
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(rotated));

        // When / Then
        assertThatThrownBy(() -> useCase().execute(new RefreshCommand(PLAIN)))
                .isInstanceOf(RefreshTokenReusedException.class);
        verify(refreshTokenRepository).revokeAllActiveForUser(userId, NOW);
        verify(tokenIssuer, never()).issueFor(any());
    }

    @Test
    void should_reject_logged_out_token_without_revoking_family() {
        // Given: revocado por logout (sin sucesor) — no es robo
        RefreshToken loggedOut = RefreshToken.issue(UUID.randomUUID(), HASH,
                NOW.minus(Duration.ofDays(1)), Duration.ofDays(14));
        loggedOut.revoke(NOW.minus(Duration.ofHours(2)));
        when(refreshTokenRepository.findByTokenHash(HASH)).thenReturn(Optional.of(loggedOut));

        assertThatThrownBy(() -> useCase().execute(new RefreshCommand(PLAIN)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).revokeAllActiveForUser(any(), any());
    }
}
