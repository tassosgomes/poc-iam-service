package com.platform.authz.sdk.dto;

import java.util.Objects;

/**
 * Response DTO for the permission check endpoint.
 * Maps to {@code POST /v1/authz/check}.
 */
public record CheckPermissionResponse(boolean allowed, String source) {

    public CheckPermissionResponse {
        Objects.requireNonNull(source, "source must not be null");
    }
}
