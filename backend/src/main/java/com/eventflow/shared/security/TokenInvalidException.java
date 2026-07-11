package com.eventflow.shared.security;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class TokenInvalidException extends DomainException {

    public TokenInvalidException(String detail) {
        super(ErrorCode.TOKEN_INVALID, detail);
    }
}
