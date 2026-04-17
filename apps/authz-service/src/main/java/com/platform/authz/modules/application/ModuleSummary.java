package com.platform.authz.modules.application;

import com.platform.authz.modules.domain.ModuleKeyStatus;
import java.time.Instant;
import java.util.UUID;

public record ModuleSummary(
        UUID moduleId,
        String name,
        String allowedPrefix,
        String description,
        Instant createdAt,
        Instant lastHeartbeatAt,
        ModuleHeartbeatStatus heartbeatStatus,
        UUID activeKeyId,
        ModuleKeyStatus activeKeyStatus,
        Instant activeKeyCreatedAt,
        long activeKeyAgeDays
) {
}
