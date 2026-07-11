package com.eventflow.modules.identity.application.result;

import com.eventflow.modules.identity.domain.User;

import java.util.UUID;

/** Resultado de emisión de tokens; refreshTokenId es interno (encadena la rotación). */
public record AuthResult(String accessToken, long expiresInSeconds, String refreshToken,
                         UUID refreshTokenId, User user) {

    public AuthResult withUser(User newUser) {
        return new AuthResult(accessToken, expiresInSeconds, refreshToken, refreshTokenId, newUser);
    }
}
