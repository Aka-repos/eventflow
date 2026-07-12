package com.eventflow.modules.ordering.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class OrderNotPendingException extends DomainException {

    public OrderNotPendingException(String currentStatus) {
        super(ErrorCode.ORDER_NOT_PENDING, "La orden no está pendiente (estado actual: " + currentStatus + ")");
    }
}
