package com.platform.authz.shared.security;

import java.time.Instant;
import java.util.Objects;

public record ModuleContext(
        String moduleId,
        String allowedPrefix,
        Instant keyIssuedAt
) {

    public ModuleContext {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId must not be blank");
        }

        if (allowedPrefix == null || allowedPrefix.isBlank()) {
            throw new IllegalArgumentException("allowedPrefix must not be blank");
        }

        Objects.requireNonNull(keyIssuedAt, "keyIssuedAt must not be null");
    }
}
