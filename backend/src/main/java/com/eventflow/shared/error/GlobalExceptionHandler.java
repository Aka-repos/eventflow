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

    @ExceptionHandler(VersionConflictException.class)
    ProblemDetail handleVersionConflict(VersionConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemFactory.from(ex.errorCode(), ex.getMessage(), request.getRequestURI());
        problem.setProperty("conflictVersion", ex.currentVersion());
        return problem;
    }

    @ExceptionHandler(SemanticValidationException.class)
    ProblemDetail handleSemanticValidation(SemanticValidationException ex, HttpServletRequest request) {
        return ProblemFactory.validation(request.getRequestURI(), ex.fieldErrors());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(HttpServletRequest request) {
        return ProblemFactory.from(ErrorCode.VERSION_CONFLICT,
                "El recurso fue modificado por otra operación; recarga e intenta de nuevo",
                request.getRequestURI());
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

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(org.springframework.web.bind.MissingRequestHeaderException ex,
                                      HttpServletRequest request) {
        return ProblemFactory.from(ErrorCode.MALFORMED_REQUEST,
                "Falta el header requerido " + ex.getHeaderName(), request.getRequestURI());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,
                                     HttpServletRequest request) {
        return ProblemFactory.from(ErrorCode.MALFORMED_REQUEST,
                "El parámetro '" + ex.getName() + "' tiene un formato inválido", request.getRequestURI());
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
