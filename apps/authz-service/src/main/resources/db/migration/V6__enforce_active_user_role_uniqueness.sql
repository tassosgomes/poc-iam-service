CREATE UNIQUE INDEX IF NOT EXISTS uk_user_role_active_user_role
    ON user_role (user_id, role_id)
    WHERE revoked_at IS NULL;
