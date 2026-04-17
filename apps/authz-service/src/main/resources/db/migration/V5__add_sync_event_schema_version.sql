ALTER TABLE sync_event
    ADD COLUMN schema_version VARCHAR(32) NOT NULL DEFAULT '1.0';
