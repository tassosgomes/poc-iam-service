package com.platform.authz.modules.application;

import com.platform.authz.audit.application.AuditEventPublisher;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.config.AuthzLifecycleProperties;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaleModuleDetector {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaleModuleDetector.class);

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final AuthzLifecycleProperties lifecycleProperties;
    private final Clock clock;

    public StaleModuleDetector(
            ModuleRepository moduleRepository,
            PermissionRepository permissionRepository,
            AuditEventPublisher auditEventPublisher,
            AuthzLifecycleProperties lifecycleProperties,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher must not be null");
        this.lifecycleProperties = Objects.requireNonNull(lifecycleProperties, "lifecycleProperties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(fixedRateString = "${authz.lifecycle.detector-rate}")
    @Transactional
    public void run() {
        Instant now = Instant.now(clock);
        Instant staleThreshold = now.minus(lifecycleProperties.staleAfter());
        List<Module> staleModules = moduleRepository.findByLastHeartbeatAtBefore(staleThreshold);
        int markedPermissions = 0;

        for (Module module : staleModules) {
            int moduleMarkedPermissions = markModulePermissionsAsStale(module, now);
            markedPermissions += moduleMarkedPermissions;

            if (moduleMarkedPermissions > 0) {
                auditEventPublisher.publish(buildAuditEvent(module, moduleMarkedPermissions, now));
            }
        }

        LOGGER.info(
                "detector.run found_stale_modules={} marked_permissions={}",
                staleModules.size(),
                markedPermissions
        );
    }

    private int markModulePermissionsAsStale(Module module, Instant now) {
        List<Permission> activePermissions = permissionRepository.findByModuleIdAndStatusIn(
                module.id(),
                List.of(PermissionStatus.ACTIVE)
        );
        List<Permission> permissionsToSave = new ArrayList<>();

        for (Permission permission : activePermissions) {
            if (permission.markStale(now)) {
                permissionsToSave.add(permission);
            }
        }

        if (permissionsToSave.isEmpty()) {
            return 0;
        }

        permissionRepository.saveAll(permissionsToSave);
        return permissionsToSave.size();
    }

    private AuditEvent buildAuditEvent(Module module, int markedPermissions, Instant now) {
        String lastHeartbeatAt = module.lastHeartbeatAt() != null ? module.lastHeartbeatAt().toString() : "";
        String payloadHash = hashPayload(
                AuditEventType.MODULE_WENT_STALE.name(),
                module.id().toString(),
                module.name(),
                lastHeartbeatAt,
                Integer.toString(markedPermissions),
                now.toString()
        );

        return new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.MODULE_WENT_STALE,
                null,
                module.id().toString(),
                Map.of(
                        "moduleId", module.id().toString(),
                        "moduleName", module.name(),
                        "lastHeartbeatAt", lastHeartbeatAt,
                        "markedPermissions", markedPermissions,
                        "payloadHash", payloadHash
                ),
                null,
                now
        );
    }

    private String hashPayload(String... values) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            String canonicalPayload = String.join("|", values);
            return HexFormat.of().formatHex(messageDigest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
