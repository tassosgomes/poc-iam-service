CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE module (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(64) UNIQUE NOT NULL,
    allowed_prefix VARCHAR(64) UNIQUE NOT NULL,
    description TEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ
);

CREATE TABLE module_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id UUID NOT NULL REFERENCES module(id),
    key_hash TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    rotated_at TIMESTAMPTZ,
    grace_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_module_key_status
        CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'REVOKED'))
);

CREATE INDEX idx_module_key_module_active
    ON module_key(module_id)
    WHERE status = 'ACTIVE';

CREATE TABLE permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id UUID NOT NULL REFERENCES module(id),
    code VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    sunset_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_permission_module_code UNIQUE (module_id, code),
    CONSTRAINT chk_permission_status
        CHECK (status IN ('ACTIVE', 'DEPRECATED', 'STALE', 'REMOVED'))
);

CREATE TABLE role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id UUID NOT NULL REFERENCES module(id),
    name VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_role_module_name UNIQUE (module_id, name)
);

CREATE TABLE role_permission (
    role_id UUID NOT NULL REFERENCES role(id),
    permission_id UUID NOT NULL REFERENCES permission(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    role_id UUID NOT NULL REFERENCES role(id),
    assigned_by VARCHAR(128) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by VARCHAR(128)
);

CREATE INDEX idx_user_role_user_active
    ON user_role(user_id)
    WHERE revoked_at IS NULL;

CREATE TABLE audit_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128),
    target VARCHAR(256),
    payload JSONB NOT NULL,
    source_ip VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_event_type_time
    ON audit_event(event_type, occurred_at DESC);

CREATE INDEX idx_audit_event_target_time
    ON audit_event(target, occurred_at DESC);
