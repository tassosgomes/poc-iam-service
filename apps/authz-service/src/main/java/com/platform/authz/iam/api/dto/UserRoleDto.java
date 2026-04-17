package com.platform.authz.iam.api.dto;

import java.time.Instant;
import java.util.UUID;

public record UserRoleDto(
        String userId,
        UUID roleId,
        UUID moduleId,
        String roleName,
        String assignedBy,
        Instant assignedAt
) {
}
