package com.platform.authz.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SyncEvent(
        UUID id,
        UUID moduleId,
        String payloadHash,
        String schemaVersion,
        int permissionCount,
        int added,
        int updated,
        int deprecated,
        String catalogVersion,
        Instant occurredAt
) {

    public SyncEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(moduleId, "moduleId must not be null");

        if (payloadHash == null || payloadHash.isBlank()) {
            throw new IllegalArgumentException("payloadHash must not be blank");
        }

        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }

        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new IllegalArgumentException("catalogVersion must not be blank");
        }

        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static SyncEvent create(
            UUID moduleId,
            String payloadHash,
            String schemaVersion,
            int permissionCount,
            int added,
            int updated,
            int deprecated,
            String catalogVersion,
            Instant occurredAt
    ) {
        return new SyncEvent(
                UUID.randomUUID(),
                moduleId,
                payloadHash,
                schemaVersion,
                permissionCount,
                added,
                updated,
                deprecated,
                catalogVersion,
                occurredAt
        );
    }
}
