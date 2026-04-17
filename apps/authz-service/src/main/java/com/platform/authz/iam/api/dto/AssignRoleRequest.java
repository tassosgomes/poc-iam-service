package com.platform.authz.iam.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRoleRequest(
        @NotNull(message = "roleId is required")
        UUID roleId
) {
}
