package com.platform.authz.sdk.dto;

import java.util.List;
import java.util.Objects;

/**
 * Request DTO for the catalog sync endpoint.
 * Maps to {@code POST /v1/catalog/sync}.
 */
public record SyncRequest(
        String moduleId,
        String schemaVersion,
        String payloadHash,
        List<PermissionDeclaration> permissions
) {

    public SyncRequest {
        Objects.requireNonNull(moduleId, "moduleId must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(payloadHash, "payloadHash must not be null");
        permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
    }

    /**
     * A single permission declaration within a sync request.
     */
    public record PermissionDeclaration(String code, String description) {

        public PermissionDeclaration {
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(description, "description must not be null");
        }
    }
}
