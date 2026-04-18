package com.platform.authz.sdk.dto;

import java.util.Objects;

/**
 * Request DTO for the permission check endpoint.
 * Maps to {@code POST /v1/authz/check}.
 */
public record CheckPermissionRequest(String userId, String permission) {

    public CheckPermissionRequest {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
    }
}
