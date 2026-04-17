package com.platform.authz.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SyncRequest(
        @NotBlank(message = "moduleId is required")
        String moduleId,

        @NotBlank(message = "schemaVersion is required")
        String schemaVersion,

        @NotBlank(message = "payloadHash is required")
        @Size(max = 128, message = "payloadHash must have at most 128 characters")
        String payloadHash,

        @NotEmpty(message = "permissions must not be empty")
        @Valid
        List<PermissionDeclaration> permissions
) {
}
