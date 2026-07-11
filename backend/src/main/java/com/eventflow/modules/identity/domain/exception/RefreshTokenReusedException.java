package com.eventflow.modules.identity.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** Reuso de un token ya rotado = posible robo; la familia completa queda revocada. */
public class RefreshTokenReusedException extends DomainException {

    public RefreshTokenReusedException() {
        super(ErrorCode.REFRESH_TOKEN_REUSED, "Reuso de refresh token detectado; sesión revocada");
    }
}
