package com.eventflow.shared.error;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Construye ProblemDetail RFC 9457 con las extensiones EventFlow (docs/api/02). */
public final class ProblemFactory {

    public static final String ERROR_TYPE_BASE = "https://api.eventflow.app/errors/";

    private ProblemFactory() {
    }

    public static ProblemDetail from(ErrorCode code, String detail, String instancePath) {
        ProblemDetail problem = ProblemDetail.forStatus(code.status());
        problem.setType(URI.create(ERROR_TYPE_BASE + code.code()));
        problem.setTitle(code.title());
        problem.setDetail(detail);
        if (instancePath != null) {
            problem.setInstance(URI.create(instancePath));
        }
        problem.setProperty("code", code.code());
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("traceId", MDC.get("correlationId"));
        return problem;
    }

    public static ProblemDetail validation(String instancePath, List<Map<String, String>> fieldErrors) {
        ProblemDetail problem = from(ErrorCode.VALIDATION_ERROR,
                "La solicitud contiene " + fieldErrors.size() + " campo(s) inválido(s)", instancePath);
        problem.setProperty("errors", fieldErrors);
        return problem;
    }
}
