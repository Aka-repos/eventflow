package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class CategoryInUseException extends DomainException {

    public CategoryInUseException() {
        super(ErrorCode.CATEGORY_IN_USE, "La categoría tiene eventos asociados y no puede eliminarse");
    }
}
