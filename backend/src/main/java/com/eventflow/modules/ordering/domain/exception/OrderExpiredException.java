package com.eventflow.modules.ordering.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class OrderExpiredException extends DomainException {

    public OrderExpiredException() {
        super(ErrorCode.ORDER_EXPIRED, "La ventana de pago de la orden expiró; el inventario fue liberado");
    }
}
