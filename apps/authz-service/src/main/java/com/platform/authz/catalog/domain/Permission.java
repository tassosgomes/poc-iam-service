package com.platform.authz.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Permission {

    private final UUID id;
    private final UUID moduleId;
    private final String code;
    private String description;
    private PermissionStatus status;
    private Instant sunsetAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public Permission(
            UUID id,
            UUID moduleId,
            String code,
            String description,
            PermissionStatus status,
            Instant sunsetAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId must not be null");
        this.code = requireText(code, "code");
        this.description = requireText(description, "description");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.sunsetAt = sunsetAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Permission createActive(UUID moduleId, String code, String description, Instant now) {
        return new Permission(
                UUID.randomUUID(),
                moduleId,
                code,
                description,
                PermissionStatus.ACTIVE,
                null,
                now,
                now
        );
    }

    public boolean updateDescription(String newDescription, Instant now) {
        Objects.requireNonNull(newDescription, "newDescription must not be null");
        if (this.description.equals(newDescription.trim())) {
            return false;
        }
        this.description = newDescription.trim();
        this.updatedAt = now;
        return true;
    }

    public void reactivate(Instant now) {
        this.status = PermissionStatus.ACTIVE;
        this.sunsetAt = null;
        this.updatedAt = now;
    }

    public void deprecate(Instant sunsetAt, Instant now) {
        this.status = PermissionStatus.DEPRECATED;
        this.sunsetAt = Objects.requireNonNull(sunsetAt, "sunsetAt must not be null");
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID moduleId() {
        return moduleId;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public PermissionStatus status() {
        return status;
    }

    public Instant sunsetAt() {
        return sunsetAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s must not be blank".formatted(fieldName));
        }
        return value.trim();
    }
}
