package com.platform.authz.iam.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserRoleAssignment(
        UUID id,
        String userId,
        UUID roleId,
        String assignedBy,
        Instant assignedAt,
        Instant revokedAt,
        String revokedBy
) {
    public UserRoleAssignment {
        Objects.requireNonNull(id, "id must not be null");
        userId = requireText(userId, "userId");
        Objects.requireNonNull(roleId, "roleId must not be null");
        assignedBy = requireText(assignedBy, "assignedBy");
        Objects.requireNonNull(assignedAt, "assignedAt must not be null");
        revokedBy = sanitizeText(revokedBy);
        validateRevocation(revokedAt, revokedBy);
    }

    public static UserRoleAssignment assign(
            String userId,
            UUID roleId,
            String assignedBy,
            Instant assignedAt
    ) {
        return new UserRoleAssignment(
                UUID.randomUUID(),
                userId,
                roleId,
                assignedBy,
                assignedAt,
                null,
                null
        );
    }

    public UserRoleAssignment revoke(String revokedBy, Instant revokedAt) {
        return new UserRoleAssignment(
                id,
                userId,
                roleId,
                assignedBy,
                assignedAt,
                Objects.requireNonNull(revokedAt, "revokedAt must not be null"),
                requireText(revokedBy, "revokedBy")
        );
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    private static void validateRevocation(Instant revokedAt, String revokedBy) {
        boolean hasRevocationTimestamp = revokedAt != null;
        boolean hasRevocationActor = revokedBy != null;
        if (hasRevocationTimestamp != hasRevocationActor) {
            throw new IllegalArgumentException("revokedAt and revokedBy must be provided together");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s must not be blank".formatted(fieldName));
        }
        return value.trim();
    }

    private static String sanitizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
