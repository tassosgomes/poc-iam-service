package com.platform.authz.iam.api.dto;

import jakarta.validation.constraints.Size;

public record CloneRoleRequest(
        @Size(max = 64, message = "name must have at most 64 characters")
        String name
) {
}
