package com.platform.authz.shared.api;

import java.net.URI;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class ProblemDetailFactory {
    private static final String ERROR_BASE_URI = "https://authz.platform/errors/";

    public ProblemDetail create(
            HttpStatusCode status,
            String type,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(ERROR_BASE_URI + type));
        problemDetail.setTitle(title);

        if (request != null) {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
        }

        problemDetail.setProperty("traceId", resolveTraceId());
        return problemDetail;
    }

    public ResponseEntity<ProblemDetail> buildResponse(
            HttpStatus status,
            String type,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(create(status, type, title, detail, request));
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }
}
