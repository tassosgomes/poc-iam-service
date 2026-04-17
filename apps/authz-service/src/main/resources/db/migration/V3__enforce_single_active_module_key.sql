DROP INDEX IF EXISTS idx_module_key_module_active;

CREATE UNIQUE INDEX uq_module_key_module_active
    ON module_key(module_id)
    WHERE status = 'ACTIVE';
