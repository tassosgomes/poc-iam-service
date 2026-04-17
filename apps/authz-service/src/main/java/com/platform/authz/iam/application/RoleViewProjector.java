package com.platform.authz.iam.application;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.iam.domain.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
class RoleViewProjector {

    private final PermissionRepository permissionRepository;

    RoleViewProjector(PermissionRepository permissionRepository) {
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
    }

    RoleView project(Role role) {
        return projectAll(List.of(role)).getFirst();
    }

    List<RoleView> projectAll(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        Set<UUID> permissionIds = roles.stream()
                .flatMap(role -> role.permissionIds().stream())
                .collect(java.util.stream.Collectors.toSet());

        Map<UUID, Permission> permissionsById = permissionRepository.findByIds(permissionIds).stream()
                .collect(java.util.stream.Collectors.toMap(Permission::id, Function.identity()));

        return roles.stream()
                .map(role -> toView(role, permissionsById))
                .toList();
    }

    private RoleView toView(Role role, Map<UUID, Permission> permissionsById) {
        List<RolePermissionView> permissions = role.permissionIds().stream()
                .map(permissionsById::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Permission::code))
                .map(permission -> new RolePermissionView(
                        permission.id(),
                        permission.code(),
                        permission.description(),
                        permission.status()
                ))
                .toList();

        return new RoleView(
                role.id(),
                role.moduleId(),
                role.name(),
                role.description(),
                role.createdBy(),
                role.createdAt(),
                permissions
        );
    }
}
