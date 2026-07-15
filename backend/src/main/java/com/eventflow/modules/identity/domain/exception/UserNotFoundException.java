package com.eventflow.modules.identity.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 404 si el sujeto del token ya no existe (cuenta borrada con token aún vigente). */
public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super(ErrorCode.NOT_FOUND, "El usuario no existe");
    }
}
