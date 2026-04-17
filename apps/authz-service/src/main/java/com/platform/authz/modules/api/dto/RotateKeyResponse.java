package com.platform.authz.modules.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RotateKeyResponse(
        UUID moduleId,
        UUID keyId,
        String secret,
        Instant createdAt,
        Instant graceExpiresAt
) {

    @Override
    public String toString() {
        return "RotateKeyResponse[moduleId=%s, keyId=%s, secret=***, createdAt=%s, graceExpiresAt=%s]"
                .formatted(moduleId, keyId, createdAt, graceExpiresAt);
    }
}
