package com.eventflow.modules.refunds.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 404 también para reembolsos de eventos ajenos (anti-enumeración). */
public class RefundNotFoundException extends DomainException {

    public RefundNotFoundException() {
        super(ErrorCode.NOT_FOUND, "El reembolso no existe");
    }
}
