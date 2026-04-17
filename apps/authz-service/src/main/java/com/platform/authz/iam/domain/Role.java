package com.platform.authz.iam.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record Role(
        UUID id,
        UUID moduleId,
        String name,
        String description,
        String createdBy,
        Instant createdAt,
        Set<RolePermission> permissions
) {
    private static final Pattern ROLE_NAME_PATTERN = Pattern.compile("^[A-Z]+(?:_[A-Z0-9]+)+$");

    public Role {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(moduleId, "moduleId must not be null");
        name = requireRoleName(name);
        description = requireText(description, "description");
        createdBy = requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        permissions = sanitizePermissions(id, permissions);
    }

    public static Role create(
            UUID moduleId,
            String name,
            String description,
            String createdBy,
            Instant createdAt,
            Collection<UUID> permissionIds
    ) {
        UUID roleId = UUID.randomUUID();
        return new Role(
                roleId,
                moduleId,
                name,
                description,
                createdBy,
                createdAt,
                toRolePermissions(roleId, permissionIds)
        );
    }

    public Role update(String name, String description, Collection<UUID> permissionIds) {
        return new Role(
                id,
                moduleId,
                name,
                description,
                createdBy,
                createdAt,
                toRolePermissions(id, permissionIds)
        );
    }

    public Role cloneAs(String clonedName, String actor, Instant createdAt) {
        return create(
                moduleId,
                clonedName,
                description,
                actor,
                createdAt,
                permissionIds()
        );
    }

    public Set<UUID> permissionIds() {
        LinkedHashSet<UUID> permissionIds = new LinkedHashSet<>();
        permissions.forEach(permission -> permissionIds.add(permission.permissionId()));
        return Set.copyOf(permissionIds);
    }

    private static String requireRoleName(String name) {
        String normalizedName = requireText(name, "name");
        if (!ROLE_NAME_PATTERN.matcher(normalizedName).matches()) {
            throw new InvalidRoleNameException(normalizedName);
        }
        return normalizedName;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s must not be blank".formatted(fieldName));
        }
        return value.trim();
    }

    private static Set<RolePermission> sanitizePermissions(UUID roleId, Set<RolePermission> permissions) {
        if (permissions == null) {
            return Set.of();
        }

        LinkedHashSet<RolePermission> sanitized = new LinkedHashSet<>();
        permissions.forEach(permission -> {
            Objects.requireNonNull(permission, "permission must not be null");
            if (!permission.roleId().equals(roleId)) {
                throw new IllegalArgumentException("permission roleId must match role id");
            }
            sanitized.add(permission);
        });
        return Set.copyOf(sanitized);
    }

    private static Set<RolePermission> toRolePermissions(UUID roleId, Collection<UUID> permissionIds) {
        if (permissionIds == null) {
            return Set.of();
        }

        LinkedHashSet<RolePermission> permissions = new LinkedHashSet<>();
        permissionIds.stream()
                .filter(Objects::nonNull)
                .forEach(permissionId -> permissions.add(new RolePermission(roleId, permissionId)));

        return Set.copyOf(permissions);
    }
}
