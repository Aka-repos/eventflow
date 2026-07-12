package com.eventflow.modules.ticketing.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class TicketTypeHasSalesException extends DomainException {

    public TicketTypeHasSalesException() {
        super(ErrorCode.TICKET_TYPE_HAS_SALES,
                "La tarifa tiene boletos vendidos: solo admite descripción, ventana de venta y aumento de cupo");
    }
}
