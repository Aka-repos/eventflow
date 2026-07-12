package com.eventflow.modules.ordering.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class SalesWindowClosedException extends DomainException {

    public SalesWindowClosedException(String detail) {
        super(ErrorCode.SALES_WINDOW_CLOSED, detail);
    }
}
