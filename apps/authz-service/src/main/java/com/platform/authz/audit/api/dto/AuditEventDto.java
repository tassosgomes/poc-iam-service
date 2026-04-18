package com.platform.authz.audit.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventDto(
        UUID id,
        String eventType,
        String actorId,
        String target,
        Map<String, Object> payload,
        String sourceIp,
        Instant occurredAt
) {
}
