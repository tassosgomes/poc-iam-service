CREATE INDEX IF NOT EXISTS idx_audit_event_module_id_time
    ON audit_event ((payload ->> 'moduleId'), occurred_at DESC);
