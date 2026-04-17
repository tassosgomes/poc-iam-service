CREATE TABLE sync_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id UUID NOT NULL REFERENCES module(id),
    payload_hash VARCHAR(128) NOT NULL,
    permission_count INT NOT NULL,
    added INT NOT NULL DEFAULT 0,
    updated INT NOT NULL DEFAULT 0,
    deprecated INT NOT NULL DEFAULT 0,
    catalog_version VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sync_event_module_occurred
    ON sync_event(module_id, occurred_at DESC);
