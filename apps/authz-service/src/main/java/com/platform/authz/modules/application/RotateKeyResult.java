package com.platform.authz.modules.application;

import java.time.Instant;
import java.util.UUID;

public record RotateKeyResult(
        UUID moduleId,
        UUID keyId,
        String secret,
        Instant createdAt,
        Instant graceExpiresAt
) {

    @Override
    public String toString() {
        return "RotateKeyResult[moduleId=%s, keyId=%s, secret=***, createdAt=%s, graceExpiresAt=%s]"
                .formatted(moduleId, keyId, createdAt, graceExpiresAt);
    }
}
