package com.platform.authz.audit.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEventSearchCriteria(
        AuditEventType eventType,
        UUID moduleId,
        String actorId,
        Instant from,
        Instant to,
        int page,
        int size
) {
}
