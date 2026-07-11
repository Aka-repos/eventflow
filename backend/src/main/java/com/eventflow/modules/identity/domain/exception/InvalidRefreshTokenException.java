package com.eventflow.modules.identity.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(ErrorCode.TOKEN_INVALID, "El refresh token no es válido o expiró");
    }
}
