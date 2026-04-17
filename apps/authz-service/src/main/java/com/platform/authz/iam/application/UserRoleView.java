package com.platform.authz.iam.application;

import java.time.Instant;
import java.util.UUID;

public record UserRoleView(
        String userId,
        UUID roleId,
        UUID moduleId,
        String roleName,
        String assignedBy,
        Instant assignedAt
) {
}
