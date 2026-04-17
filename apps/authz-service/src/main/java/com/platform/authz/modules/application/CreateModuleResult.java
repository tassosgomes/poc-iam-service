package com.platform.authz.modules.application;

import java.time.Instant;
import java.util.UUID;

public record CreateModuleResult(
        UUID moduleId,
        String name,
        String allowedPrefix,
        String secret,
        Instant createdAt
) {

    @Override
    public String toString() {
        return "CreateModuleResult[moduleId=%s, name=%s, allowedPrefix=%s, secret=***, createdAt=%s]"
                .formatted(moduleId, name, allowedPrefix, createdAt);
    }
}
