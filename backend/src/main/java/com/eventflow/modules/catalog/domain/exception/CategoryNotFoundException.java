package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class CategoryNotFoundException extends DomainException {

    public CategoryNotFoundException() {
        super(ErrorCode.NOT_FOUND, "La categoría no existe");
    }
}
