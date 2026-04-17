package com.platform.authz.modules.application;

import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ModuleSummaryProjector {
    private static final Duration STALE_HEARTBEAT_THRESHOLD = Duration.ofDays(7);

    public ModuleSummary project(Module module, Optional<ModuleKey> activeKey, Instant now) {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(activeKey, "activeKey must not be null");
        Objects.requireNonNull(now, "now must not be null");

        Instant activeKeyCreatedAt = activeKey.map(ModuleKey::createdAt).orElse(null);
        long activeKeyAgeDays = activeKeyCreatedAt != null
                ? Duration.between(activeKeyCreatedAt, now).toDays()
                : 0L;

        return new ModuleSummary(
                module.id(),
                module.name(),
                module.allowedPrefix(),
                module.description(),
                module.createdAt(),
                module.lastHeartbeatAt(),
                resolveHeartbeatStatus(module.lastHeartbeatAt(), now),
                activeKey.map(ModuleKey::id).orElse(null),
                activeKey.map(ModuleKey::status).orElse(null),
                activeKeyCreatedAt,
                activeKeyAgeDays
        );
    }

    private ModuleHeartbeatStatus resolveHeartbeatStatus(Instant lastHeartbeatAt, Instant now) {
        if (lastHeartbeatAt == null) {
            return ModuleHeartbeatStatus.NEVER_REPORTED;
        }

        if (lastHeartbeatAt.isBefore(now.minus(STALE_HEARTBEAT_THRESHOLD))) {
            return ModuleHeartbeatStatus.STALE;
        }

        return ModuleHeartbeatStatus.HEALTHY;
    }
}
