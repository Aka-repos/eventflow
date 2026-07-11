package com.eventflow.modules.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");

    private RefreshToken issued() {
        return RefreshToken.issue(UUID.randomUUID(), "hash-abc", NOW, Duration.ofDays(14));
    }

    @Test
    void should_be_active_when_issued_and_not_expired() {
        assertThat(issued().isActive(NOW.plus(Duration.ofDays(13)))).isTrue();
    }

    @Test
    void should_be_inactive_when_expired() {
        assertThat(issued().isActive(NOW.plus(Duration.ofDays(15)))).isFalse();
    }

    @Test
    void should_be_inactive_when_revoked() {
        // Given
        RefreshToken token = issued();
        // When
        token.revoke(NOW);
        // Then
        assertThat(token.isActive(NOW)).isFalse();
        assertThat(token.wasReplaced()).isFalse();
    }

    @Test
    void should_record_rotation_chain_when_replaced() {
        // Given: rotación — el token usado se invalida y apunta a su sucesor (detección de robo)
        RefreshToken old = issued();
        UUID successorId = UUID.randomUUID();
        // When
        old.markReplacedBy(successorId, NOW);
        // Then
        assertThat(old.isActive(NOW)).isFalse();
        assertThat(old.wasReplaced()).isTrue();
        assertThat(old.getReplacedBy()).isEqualTo(successorId);
    }
}
