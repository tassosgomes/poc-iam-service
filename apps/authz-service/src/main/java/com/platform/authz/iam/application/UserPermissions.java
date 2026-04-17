package com.platform.authz.iam.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record UserPermissions(
        String userId,
        Set<String> permissions,
        Instant resolvedAt,
        Duration ttl
) {
    public UserPermissions {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        userId = userId.trim();
        permissions = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(permissions, "permissions must not be null")
        ));
        resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        ttl = Objects.requireNonNull(ttl, "ttl must not be null");
    }
}
