package com.eventflow.modules.catalog.domain.exception;

import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;

public class CategoryNameTakenException extends DomainException {

    public CategoryNameTakenException() {
        super(ErrorCode.CATEGORY_NAME_TAKEN, "Ya existe una categoría con ese nombre");
    }
}
