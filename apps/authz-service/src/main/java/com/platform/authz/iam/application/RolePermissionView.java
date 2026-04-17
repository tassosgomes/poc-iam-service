package com.platform.authz.iam.application;

import com.platform.authz.catalog.domain.PermissionStatus;
import java.util.UUID;

public record RolePermissionView(
        UUID permissionId,
        String code,
        String description,
        PermissionStatus status
) {
}
