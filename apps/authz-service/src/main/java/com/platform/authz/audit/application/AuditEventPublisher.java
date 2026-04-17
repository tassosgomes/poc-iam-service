package com.platform.authz.audit.application;

import com.platform.authz.audit.domain.AuditEvent;

public interface AuditEventPublisher {

    void publish(AuditEvent event);
}
