package com.platform.authz.audit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public record AuditEventQueryParams(
        @Pattern(
                regexp = "MODULE_CREATED|KEY_ROTATED|CATALOG_SYNC|ROLE_ASSIGNED|ROLE_REVOKED|KEY_AUTH_FAILED",
                message = "eventType must be a supported audit event type"
        )
        String eventType,
        UUID moduleId,
        String actorId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to,
        @Min(1)
        Integer page,
        @Min(1)
        @Max(100)
        Integer size
) {
}
