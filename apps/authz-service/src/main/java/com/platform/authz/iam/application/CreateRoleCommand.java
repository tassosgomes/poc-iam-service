package com.platform.authz.iam.application;

import java.util.Set;
import java.util.UUID;

public record CreateRoleCommand(
        UUID moduleId,
        String name,
        String description,
        Set<UUID> permissionIds,
        String createdBy
) {
}
