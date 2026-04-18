package com.platform.authz.shared.observability;

import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.config.AuthzLifecycleProperties;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlatformGauges {
    private final ModuleRepository moduleRepository;
    private final ModuleKeyRepository moduleKeyRepository;
    private final PermissionRepository permissionRepository;
    private final AuthzLifecycleProperties lifecycleProperties;
    private final Clock clock;

    public PlatformGauges(
            ModuleRepository moduleRepository,
            ModuleKeyRepository moduleKeyRepository,
            PermissionRepository permissionRepository,
            AuthzLifecycleProperties lifecycleProperties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.moduleKeyRepository = Objects.requireNonNull(moduleKeyRepository, "moduleKeyRepository must not be null");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
        this.lifecycleProperties = Objects.requireNonNull(lifecycleProperties, "lifecycleProperties must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");

        Gauge.builder("authz_stale_modules_count", this, PlatformGauges::countStaleModules)
                .description("Number of modules with heartbeat older than the configured stale threshold")
                .register(meterRegistry);

        Gauge.builder("authz_module_key_age_max_days", this, PlatformGauges::maxModuleKeyAgeDays)
                .description("Maximum age in days among active module keys")
                .register(meterRegistry);

        Gauge.builder("authz_permissions_deprecated_count", this, PlatformGauges::countDeprecatedPermissions)
                .description("Number of permissions currently marked as deprecated")
                .register(meterRegistry);
    }

    private double countStaleModules() {
        Instant threshold = Instant.now(clock).minus(lifecycleProperties.staleAfter());
        return moduleRepository.findByLastHeartbeatAtBefore(threshold).size();
    }

    private double maxModuleKeyAgeDays() {
        Instant now = Instant.now(clock);
        Map<UUID, ModuleKey> activeKeysByModuleId = moduleKeyRepository.findActiveByModuleIds(
                moduleRepository.findAll().stream().map(Module::id).toList()
        );

        return activeKeysByModuleId.values().stream()
                .mapToDouble(moduleKey -> Duration.between(moduleKey.createdAt(), now).toDays())
                .max()
                .orElse(0.0d);
    }

    private double countDeprecatedPermissions() {
        return permissionRepository.countByStatus(PermissionStatus.DEPRECATED);
    }
}
