package com.eventflow.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        log.warn("domain_error code={} detail={}", ex.errorCode().code(), ex.getMessage());
        return ProblemFactory.from(ex.errorCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "inválido" : fe.getDefaultMessage(),
                        "code", fe.getCode() == null ? "invalid" : fe.getCode()))
                .toList();
        return ProblemFactory.validation(request.getRequestURI(), errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpServletRequest request) {
        return ProblemFactory.from(ErrorCode.MALFORMED_REQUEST, "El cuerpo del request no es JSON válido",
                request.getRequestURI());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(HttpServletRequest request) {
        return ProblemFactory.from(ErrorCode.NOT_FOUND, "El recurso no existe", request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(HttpServletRequest request) {
        return ProblemFactory.from(ErrorCode.FORBIDDEN, "No tienes permiso para esta operación",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("unexpected_error uri={}", request.getRequestURI(), ex);
        return ProblemFactory.from(ErrorCode.INTERNAL_ERROR, "Error interno; reporta el traceId",
                request.getRequestURI());
    }
}
