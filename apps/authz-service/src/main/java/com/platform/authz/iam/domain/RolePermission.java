package com.platform.authz.iam.domain;

import java.util.Objects;
import java.util.UUID;

public record RolePermission(UUID roleId, UUID permissionId) {

    public RolePermission {
        Objects.requireNonNull(roleId, "roleId must not be null");
        Objects.requireNonNull(permissionId, "permissionId must not be null");
    }
}
