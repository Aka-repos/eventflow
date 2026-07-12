package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

/** 409 event_sold_out: el contrato sugiere waitlist en el payload si está habilitada (M7). */
public class TariffSoldOutException extends DomainException {

    public TariffSoldOutException(String tariffName) {
        super(ErrorCode.EVENT_SOLD_OUT, "Sin cupo disponible para la tarifa " + tariffName);
    }
}
