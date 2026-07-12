package com.eventflow.modules.ordering.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class OrderNotFoundException extends DomainException {

    public OrderNotFoundException() {
        super(ErrorCode.NOT_FOUND, "La orden no existe");
    }
}
