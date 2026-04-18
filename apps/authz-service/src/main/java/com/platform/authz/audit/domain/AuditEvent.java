package com.platform.authz.audit.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
    public AuditEvent {
        id = Objects.requireNonNull(id, "id must not be null");
        eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
