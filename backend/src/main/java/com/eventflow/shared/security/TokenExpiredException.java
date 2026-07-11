package com.eventflow.shared.security;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class TokenExpiredException extends DomainException {

    public TokenExpiredException() {
        super(ErrorCode.TOKEN_EXPIRED, "El access token expiró; usa /auth/refresh");
    }
}
