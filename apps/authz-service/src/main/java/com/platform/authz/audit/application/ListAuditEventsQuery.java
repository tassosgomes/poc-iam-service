package com.platform.authz.audit.application;

import com.platform.authz.audit.domain.AuditEventType;
import java.time.Instant;
import java.util.UUID;

public record ListAuditEventsQuery(
        AuditEventType eventType,
        UUID moduleId,
        String actorId,
        Instant from,
        Instant to,
        int page,
        int size
) {
}
