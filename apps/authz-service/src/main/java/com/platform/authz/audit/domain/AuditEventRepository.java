package com.platform.authz.audit.domain;

public interface AuditEventRepository {

    AuditEvent save(AuditEvent auditEvent);

    AuditEventPage findPage(AuditEventSearchCriteria criteria);
}
