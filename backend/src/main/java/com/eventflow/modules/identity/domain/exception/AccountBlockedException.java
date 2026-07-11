package com.eventflow.modules.identity.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class AccountBlockedException extends DomainException {

    public AccountBlockedException() {
        super(ErrorCode.ACCOUNT_BLOCKED, "La cuenta está bloqueada");
    }
}
