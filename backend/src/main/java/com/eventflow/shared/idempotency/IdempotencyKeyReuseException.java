package com.eventflow.shared.idempotency;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 422: misma clave con cuerpo distinto, o reuso mientras la operación original sigue en curso. */
public class IdempotencyKeyReuseException extends DomainException {

    public IdempotencyKeyReuseException(String detail) {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSE, detail);
    }
}
