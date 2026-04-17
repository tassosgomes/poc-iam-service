package com.platform.authz.iam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoleDto(
        UUID roleId,
        UUID moduleId,
        String name,
        String description,
        String createdBy,
        Instant createdAt,
        List<RolePermissionDto> permissions
) {
    public record RolePermissionDto(
            UUID permissionId,
            String code,
            String description,
            String status
    ) {
    }
}
