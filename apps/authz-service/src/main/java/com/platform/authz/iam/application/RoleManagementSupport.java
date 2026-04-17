package com.platform.authz.iam.application;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.iam.domain.InvalidRoleNameException;
import com.platform.authz.iam.domain.InvalidRolePermissionStatusException;
import com.platform.authz.iam.domain.PermissionModuleMismatchException;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class RoleManagementSupport {
    private static final Set<PermissionStatus> ASSIGNABLE_PERMISSION_STATUSES = Set.of(
            PermissionStatus.ACTIVE,
            PermissionStatus.DEPRECATED
    );
    private static final Pattern NON_ROLE_CHARACTER_PATTERN = Pattern.compile("[^A-Z0-9]+");

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;

    RoleManagementSupport(ModuleRepository moduleRepository, PermissionRepository permissionRepository) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
    }

    Module getModule(UUID moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException(moduleId));
    }

    void validateRoleName(Module module, String roleName) {
        Objects.requireNonNull(module, "module must not be null");
        String normalizedRoleName = Objects.requireNonNull(roleName, "roleName must not be null").trim();
        String expectedPrefix = moduleRolePrefix(module.allowedPrefix());
        if (!normalizedRoleName.startsWith(expectedPrefix + "_")) {
            throw new InvalidRoleNameException(normalizedRoleName);
        }
    }

    List<Permission> validatePermissions(UUID moduleId, Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }

        List<Permission> permissions = permissionRepository.findByIds(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new PermissionModuleMismatchException(moduleId);
        }

        permissions.stream()
                .filter(permission -> !permission.moduleId().equals(moduleId))
                .findFirst()
                .ifPresent(permission -> {
                    throw new PermissionModuleMismatchException(moduleId);
                });

        permissions.stream()
                .filter(permission -> !ASSIGNABLE_PERMISSION_STATUSES.contains(permission.status()))
                .findFirst()
                .ifPresent(permission -> {
                    throw new InvalidRolePermissionStatusException(permission.id(), permission.status().name());
                });

        return permissions.stream()
                .sorted(Comparator.comparing(Permission::code))
                .toList();
    }

    String generateCloneName(Module module, String sourceRoleName, String requestedName, java.util.function.Predicate<String> existsByName) {
        Objects.requireNonNull(module, "module must not be null");
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName.trim();
        }

        String baseName = sourceRoleName + "_COPY";
        String candidate = truncateToMaxLength(baseName, 64);
        if (!existsByName.test(candidate)) {
            return candidate;
        }

        int sequence = 2;
        while (true) {
            String sequencedCandidate = truncateToMaxLength(baseName + "_" + sequence, 64);
            if (!existsByName.test(sequencedCandidate)) {
                return sequencedCandidate;
            }
            sequence++;
        }
    }

    String moduleRolePrefix(String allowedPrefix) {
        String normalized = NON_ROLE_CHARACTER_PATTERN.matcher(allowedPrefix.trim().toUpperCase(Locale.ROOT))
                .replaceAll("_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        return normalized;
    }

    private String truncateToMaxLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
