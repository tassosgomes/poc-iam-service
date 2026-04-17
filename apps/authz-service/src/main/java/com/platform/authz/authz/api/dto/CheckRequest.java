package com.platform.authz.authz.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckRequest(
        @NotBlank(message = "userId must not be blank")
        String userId,

        @NotBlank(message = "permission must not be blank")
        String permission
) {
}
