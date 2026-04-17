package com.platform.authz.modules.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ModuleKey(
        UUID id,
        UUID moduleId,
        String keyHash,
        ModuleKeyStatus status,
        Instant rotatedAt,
        Instant graceExpiresAt,
        Instant createdAt
) {

    public ModuleKey {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(moduleId, "moduleId must not be null");

        if (keyHash == null || keyHash.isBlank()) {
            throw new IllegalArgumentException("keyHash must not be blank");
        }

        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (status == ModuleKeyStatus.SUPERSEDED
                && (rotatedAt == null || graceExpiresAt == null)) {
            throw new IllegalArgumentException("Superseded keys must include rotation metadata");
        }
    }

    public static ModuleKey createActive(UUID moduleId, String keyHash, Instant createdAt) {
        return new ModuleKey(
                UUID.randomUUID(),
                moduleId,
                keyHash,
                ModuleKeyStatus.ACTIVE,
                null,
                null,
                createdAt
        );
    }

    public ModuleKey supersede(Instant rotatedAt, Instant graceExpiresAt) {
        if (status != ModuleKeyStatus.ACTIVE) {
            throw new IllegalStateException("Only active keys can be superseded");
        }

        return new ModuleKey(
                id,
                moduleId,
                keyHash,
                ModuleKeyStatus.SUPERSEDED,
                rotatedAt,
                graceExpiresAt,
                createdAt
        );
    }
}
