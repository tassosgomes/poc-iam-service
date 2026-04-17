INSERT INTO module (
    id,
    name,
    allowed_prefix,
    description,
    created_by,
    created_at,
    last_heartbeat_at
)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'platform',
    'platform',
    'Platform administration module',
    'system',
    TIMESTAMPTZ '2026-04-16 00:00:00+00',
    TIMESTAMPTZ '2026-04-16 00:00:00+00'
);

INSERT INTO permission (
    id,
    module_id,
    code,
    description,
    status,
    sunset_at,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'platform.admin.all',
    'Grants unrestricted administrative access to the platform',
    'ACTIVE',
    NULL,
    TIMESTAMPTZ '2026-04-16 00:00:00+00',
    TIMESTAMPTZ '2026-04-16 00:00:00+00'
);

INSERT INTO role (
    id,
    module_id,
    name,
    description,
    created_by,
    created_at
)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'PLATFORM_ADMIN',
    'Global administrator role for platform operations',
    'system',
    TIMESTAMPTZ '2026-04-16 00:00:00+00'
);

INSERT INTO role_permission (role_id, permission_id)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003'
);

INSERT INTO user_role (
    id,
    user_id,
    role_id,
    assigned_by,
    assigned_at,
    revoked_at,
    revoked_by
)
VALUES (
    gen_random_uuid(),
    'user-admin',
    '00000000-0000-0000-0000-000000000002',
    'system',
    TIMESTAMPTZ '2026-04-16 00:00:00+00',
    NULL,
    NULL
);
