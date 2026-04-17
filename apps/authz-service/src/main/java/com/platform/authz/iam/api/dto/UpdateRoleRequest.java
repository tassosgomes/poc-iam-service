package com.platform.authz.iam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UpdateRoleRequest(
        @NotBlank(message = "name is required")
        @Size(max = 64, message = "name must have at most 64 characters")
        String name,
        @NotBlank(message = "description is required")
        String description,
        @NotNull(message = "permissionIds is required")
        Set<UUID> permissionIds
) {
}
