package com.platform.authz.audit.domain;

import java.util.List;

public record AuditEventPage(List<AuditEvent> events, long totalElements) {

    public AuditEventPage {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
