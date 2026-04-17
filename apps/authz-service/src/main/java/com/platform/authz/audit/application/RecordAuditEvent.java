package com.platform.authz.audit.application;

import com.platform.authz.audit.domain.AuditEvent;

public interface RecordAuditEvent {

    void record(AuditEvent event);
}
