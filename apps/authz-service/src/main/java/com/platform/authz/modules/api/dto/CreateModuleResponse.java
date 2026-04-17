package com.platform.authz.modules.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateModuleResponse(
        UUID moduleId,
        String name,
        String allowedPrefix,
        String secret,
        Instant createdAt
) {

    @Override
    public String toString() {
        return "CreateModuleResponse[moduleId=%s, name=%s, allowedPrefix=%s, secret=***, createdAt=%s]"
                .formatted(moduleId, name, allowedPrefix, createdAt);
    }
}
