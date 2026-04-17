package com.platform.authz.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        AuditEventType eventType,
        String actorId,
        String target,
        Map<String, Object> payload,
        String sourceIp,
        Instant occurredAt
) {
}
