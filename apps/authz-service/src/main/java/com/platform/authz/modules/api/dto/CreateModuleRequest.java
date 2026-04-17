package com.platform.authz.modules.api.dto;

import com.platform.authz.shared.domain.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateModuleRequest(
        @NotBlank(message = "name is required")
        @Size(max = 64, message = "name must have at most 64 characters")
        String name,

        @NotBlank(message = "allowedPrefix is required")
        @Pattern(
                regexp = ValidationPatterns.ALLOWED_PREFIX_REGEX,
                message = "allowedPrefix must match " + ValidationPatterns.ALLOWED_PREFIX_REGEX
        )
        String allowedPrefix,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must have at most 500 characters")
        String description
) {
}
