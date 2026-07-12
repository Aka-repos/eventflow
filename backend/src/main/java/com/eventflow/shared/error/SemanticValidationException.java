package com.eventflow.shared.error;

import java.util.List;
import java.util.Map;

/** Validación semántica (422) detectada en el caso de uso — p. ej. categoryId inexistente. */
public class SemanticValidationException extends DomainException {

    private final transient List<Map<String, String>> fieldErrors;

    public SemanticValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        this.fieldErrors = List.of(Map.of("field", field, "message", message, "code", "invalid"));
    }

    public List<Map<String, String>> fieldErrors() {
        return fieldErrors;
    }
}
