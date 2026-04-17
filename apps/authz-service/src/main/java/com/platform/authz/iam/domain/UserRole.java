package com.platform.authz.iam.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserRole(
        UUID id,
        String userId,
        UUID roleId,
        String assignedBy,
        Instant assignedAt,
        Instant revokedAt,
        String revokedBy
) {
    public UserRole {
        Objects.requireNonNull(id, "id must not be null");
        userId = requireText(userId, "userId");
        Objects.requireNonNull(roleId, "roleId must not be null");
        assignedBy = requireText(assignedBy, "assignedBy");
        Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        revokedBy = revokedBy == null || revokedBy.isBlank() ? null : revokedBy.trim();
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s must not be blank".formatted(fieldName));
        }
        return value.trim();
    }
}
