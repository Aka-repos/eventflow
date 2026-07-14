package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 409: el boleto no está en un estado que permita emitir/validar QR (no ACTIVE). */
public class TicketBlockedException extends DomainException {

    public TicketBlockedException(String detail) {
        super(ErrorCode.TICKET_BLOCKED, detail);
    }
}
