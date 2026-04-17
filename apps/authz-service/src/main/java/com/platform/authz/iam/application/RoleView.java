package com.platform.authz.iam.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleView(
        UUID roleId,
        UUID moduleId,
        String name,
        String description,
        String createdBy,
        Instant createdAt,
        List<RolePermissionView> permissions
) {
}
