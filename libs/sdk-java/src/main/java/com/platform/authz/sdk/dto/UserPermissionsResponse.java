package com.platform.authz.sdk.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Response DTO for the bulk-fetch user permissions endpoint.
 * Maps to {@code GET /v1/users/{userId}/permissions}.
 */
public record UserPermissionsResponse(
        String userId,
        List<String> permissions,
        Instant resolvedAt,
        long ttlSeconds
) {

    public UserPermissionsResponse {
        Objects.requireNonNull(userId, "userId must not be null");
        permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
    }
}
