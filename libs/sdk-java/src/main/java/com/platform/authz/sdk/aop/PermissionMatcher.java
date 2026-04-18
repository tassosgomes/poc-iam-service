package com.platform.authz.sdk.aop;

import java.util.Objects;
import java.util.Set;

/**
 * Matches required permissions against the user permission set, including wildcard support.
 */
public class PermissionMatcher {

    public boolean matches(Set<String> userPermissions, String requiredPermission) {
        Objects.requireNonNull(userPermissions, "userPermissions must not be null");
        Objects.requireNonNull(requiredPermission, "requiredPermission must not be null");

        if (userPermissions.contains(requiredPermission)) {
            return true;
        }

        if (requiredPermission.endsWith(".*")) {
            String prefix = requiredPermission.substring(0, requiredPermission.length() - 2);
            return userPermissions.stream()
                    .anyMatch(permission -> permission.startsWith(prefix + "."));
        }

        return userPermissions.stream()
                .filter(permission -> permission.endsWith(".*"))
                .map(permission -> permission.substring(0, permission.length() - 2))
                .anyMatch(prefix -> requiredPermission.startsWith(prefix + "."));
    }
}
