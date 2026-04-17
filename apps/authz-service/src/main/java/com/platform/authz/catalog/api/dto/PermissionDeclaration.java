package com.platform.authz.catalog.api.dto;

import com.platform.authz.shared.domain.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionDeclaration(
        @NotBlank(message = "code is required")
        @Size(max = 128, message = "code must have at most 128 characters")
        @Pattern(
                regexp = ValidationPatterns.PERMISSION_CODE_REGEX,
                message = "code must follow the pattern <allowedPrefix>.<resource>.<action> "
                        + "(first segment may include hyphens; remaining segments are snake_case; minimum 3 segments)"
        )
        String code,

        @NotBlank(message = "description is required")
        @Size(max = 500, message = "description must have at most 500 characters")
        String description
) {
}
