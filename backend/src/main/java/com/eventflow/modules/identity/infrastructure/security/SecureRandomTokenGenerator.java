package com.eventflow.modules.identity.infrastructure.security;

import com.eventflow.modules.identity.application.support.TokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Refresh tokens opacos de 256 bits, base64url sin padding. */
@Component
class SecureRandomTokenGenerator implements TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
