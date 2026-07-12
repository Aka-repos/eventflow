package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class TariffSalesWindowClosedException extends DomainException {

    public TariffSalesWindowClosedException(String tariffName) {
        super(ErrorCode.SALES_WINDOW_CLOSED, "La ventana de venta de la tarifa " + tariffName + " no está abierta");
    }
}
