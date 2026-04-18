package com.platform.authz.audit.domain;

public enum AuditEventType {
    MODULE_CREATED,
    KEY_ROTATED,
    CATALOG_SYNC,
    ROLE_ASSIGNED,
    ROLE_REVOKED,
    KEY_AUTH_FAILED
}
