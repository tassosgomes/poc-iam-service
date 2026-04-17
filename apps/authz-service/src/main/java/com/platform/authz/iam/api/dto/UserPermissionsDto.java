package com.platform.authz.iam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record UserPermissionsDto(
        String userId,
        List<String> permissions,
        Instant resolvedAt,
        long ttlSeconds
) {
    public UserPermissionsDto {
        Objects.requireNonNull(userId, "userId must not be null");
        permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
    }
}
