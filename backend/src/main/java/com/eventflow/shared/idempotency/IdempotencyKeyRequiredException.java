package com.eventflow.shared.idempotency;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 422: los POST marcados ⚡ exigen Idempotency-Key (api/01 §7). */
public class IdempotencyKeyRequiredException extends DomainException {

    public IdempotencyKeyRequiredException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Este endpoint requiere el header Idempotency-Key (UUID)");
    }
}
