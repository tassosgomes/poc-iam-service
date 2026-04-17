package com.platform.authz.iam.application;

import java.util.Set;
import java.util.UUID;

public record UpdateRoleCommand(
        UUID roleId,
        String name,
        String description,
        Set<UUID> permissionIds
) {
}
