package com.platform.authz.modules.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModuleRequest(
        @NotBlank(message = "name is required")
        @Size(max = 64, message = "name must have at most 64 characters")
        String name,

        @NotBlank(message = "allowedPrefix is required")
        @Pattern(
                regexp = "^[a-z][a-z0-9-]{1,30}$",
                message = "allowedPrefix must match ^[a-z][a-z0-9-]{1,30}$"
        )
        String allowedPrefix,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must have at most 500 characters")
        String description
) {
}
