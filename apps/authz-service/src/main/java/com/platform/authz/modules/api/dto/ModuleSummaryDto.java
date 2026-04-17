package com.platform.authz.modules.api.dto;

import com.platform.authz.modules.application.ModuleHeartbeatStatus;
import com.platform.authz.modules.domain.ModuleKeyStatus;
import java.time.Instant;
import java.util.UUID;

public record ModuleSummaryDto(
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
