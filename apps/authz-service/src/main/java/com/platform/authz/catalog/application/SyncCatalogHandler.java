package com.platform.authz.catalog.application;

import com.platform.authz.audit.application.RecordAuditEvent;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.catalog.domain.SyncEvent;
import com.platform.authz.catalog.domain.SyncEventRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.exception.ModuleIdMismatchException;
import com.platform.authz.shared.security.ModuleContext;
import com.platform.authz.shared.security.PermissionPrefixValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncCatalogHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncCatalogHandler.class);
    private static final Duration SUNSET_PERIOD = Duration.ofDays(30);
    private static final String METRIC_NAME = "authz_catalog_sync_total";

    private final ModuleRepository moduleRepository;
    private final PermissionRepository permissionRepository;
    private final SyncEventRepository syncEventRepository;
    private final PermissionPrefixValidator permissionPrefixValidator;
    private final RecordAuditEvent recordAuditEvent;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SyncCatalogHandler(
            ModuleRepository moduleRepository,
            PermissionRepository permissionRepository,
            SyncEventRepository syncEventRepository,
            PermissionPrefixValidator permissionPrefixValidator,
            RecordAuditEvent recordAuditEvent,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
        this.syncEventRepository = Objects.requireNonNull(syncEventRepository, "syncEventRepository must not be null");
        this.permissionPrefixValidator = Objects.requireNonNull(
                permissionPrefixValidator,
                "permissionPrefixValidator must not be null"
        );
        this.recordAuditEvent = Objects.requireNonNull(recordAuditEvent, "recordAuditEvent must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public SyncCatalogResult handle(SyncCatalogCommand command, ModuleContext moduleContext) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(moduleContext, "moduleContext must not be null");

        Instant now = Instant.now(clock);
        UUID moduleId = UUID.fromString(moduleContext.moduleId());

        // 0. Validate moduleId consistency between body and authentication
        if (!moduleContext.moduleId().equals(command.moduleId())) {
            throw new ModuleIdMismatchException(command.moduleId(), moduleContext.moduleId());
        }

        // 1. Validate prefix before touching DB
        List<String> permissionCodes = command.permissions().stream()
                .map(SyncCatalogCommand.PermissionEntry::code)
                .toList();
        permissionPrefixValidator.validateAll(permissionCodes, moduleContext.allowedPrefix());

        // 2. SELECT FOR UPDATE on module row to prevent concurrent syncs
        Module module = moduleRepository.findByIdForUpdate(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException(moduleId));

        // 3. Always update heartbeat
        moduleRepository.updateLastHeartbeatAt(moduleId, now);

        // 4. Check idempotency via payloadHash
        SyncEvent latestSyncEvent = syncEventRepository.findLatestByModuleId(moduleId).orElse(null);
        boolean isIdempotent = latestSyncEvent != null
                && latestSyncEvent.payloadHash().equals(command.payloadHash());

        if (isIdempotent) {
            String catalogVersion = latestSyncEvent.catalogVersion();

            incrementMetric(module.name(), "idempotent");
            LOGGER.info("catalog.sync_idempotent moduleId={} moduleName={} payloadHash={}",
                    moduleId, module.name(), command.payloadHash());

            return new SyncCatalogResult(catalogVersion, 0, 0, 0, false);
        }

        // 5. Perform diff
        DiffResult diff = performDiff(moduleId, command.permissions(), now);

        // 6. Persist changes
        if (!diff.toSave.isEmpty()) {
            permissionRepository.saveAll(diff.toSave);
        }

        // 7. Generate catalog version
        String catalogVersion = generateCatalogVersion(moduleId, now);

        // 8. Record sync event (now includes schemaVersion)
        SyncEvent syncEvent = SyncEvent.create(
                moduleId,
                command.payloadHash(),
                command.schemaVersion(),
                command.permissions().size(),
                diff.addedCount,
                diff.updatedCount,
                diff.deprecatedCount,
                catalogVersion,
                now
        );
        syncEventRepository.save(syncEvent);

        // 9. Record audit event
        recordAuditEvent.record(new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.CATALOG_SYNC,
                "module:" + module.name(),
                moduleId.toString(),
                Map.of(
                        "moduleId", moduleId.toString(),
                        "moduleName", module.name(),
                        "added", diff.addedCount,
                        "updated", diff.updatedCount,
                        "deprecated", diff.deprecatedCount,
                        "payloadHash", command.payloadHash(),
                        "schemaVersion", command.schemaVersion() != null ? command.schemaVersion() : "",
                        "catalogVersion", catalogVersion
                ),
                null,
                now
        ));

        // 10. Metrics
        boolean noLogicalChange = diff.addedCount == 0 && diff.updatedCount == 0 && diff.deprecatedCount == 0;
        if (noLogicalChange) {
            incrementMetric(module.name(), "no_change");
        }
        if (diff.addedCount > 0) {
            incrementMetric(module.name(), "added", diff.addedCount);
        }
        if (diff.updatedCount > 0) {
            incrementMetric(module.name(), "updated", diff.updatedCount);
        }
        if (diff.deprecatedCount > 0) {
            incrementMetric(module.name(), "deprecated", diff.deprecatedCount);
        }

        LOGGER.info("catalog.sync_completed moduleId={} moduleName={} added={} updated={} deprecated={} catalogVersion={}",
                moduleId, module.name(), diff.addedCount, diff.updatedCount, diff.deprecatedCount, catalogVersion);

        return new SyncCatalogResult(catalogVersion, diff.addedCount, diff.updatedCount, diff.deprecatedCount, true);
    }

    private DiffResult performDiff(UUID moduleId, List<SyncCatalogCommand.PermissionEntry> incoming, Instant now) {
        // Load existing permissions (ACTIVE + DEPRECATED)
        List<Permission> existing = permissionRepository.findByModuleIdAndStatusIn(
                moduleId,
                List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
        );

        Map<String, Permission> existingByCode = existing.stream()
                .collect(Collectors.toMap(Permission::code, Function.identity()));

        Map<String, SyncCatalogCommand.PermissionEntry> incomingByCode = incoming.stream()
                .collect(Collectors.toMap(SyncCatalogCommand.PermissionEntry::code, Function.identity()));

        List<Permission> toSave = new ArrayList<>();
        int addedCount = 0;
        int updatedCount = 0;
        int deprecatedCount = 0;

        // Process incoming permissions
        for (SyncCatalogCommand.PermissionEntry entry : incoming) {
            Permission existingPermission = existingByCode.get(entry.code());

            if (existingPermission == null) {
                // New permission → INSERT as ACTIVE
                toSave.add(Permission.createActive(moduleId, entry.code(), entry.description(), now));
                addedCount++;
            } else {
                boolean changed = false;

                // Reactivate if DEPRECATED
                boolean reactivated = false;
                if (existingPermission.status() == PermissionStatus.DEPRECATED) {
                    existingPermission.reactivate(now);
                    changed = true;
                    reactivated = true;
                    addedCount++; // Reactivation counts as added
                }

                // Update description if changed (only counts as updated when NOT reactivated)
                if (existingPermission.updateDescription(entry.description(), now)) {
                    changed = true;
                    if (!reactivated) {
                        updatedCount++;
                    }
                }

                if (changed) {
                    toSave.add(existingPermission);
                }
            }
        }

        // Mark removed permissions as DEPRECATED
        for (Permission existingPermission : existing) {
            if (existingPermission.status() == PermissionStatus.ACTIVE
                    && !incomingByCode.containsKey(existingPermission.code())) {
                existingPermission.deprecate(now.plus(SUNSET_PERIOD), now);
                toSave.add(existingPermission);
                deprecatedCount++;
            }
        }

        return new DiffResult(toSave, addedCount, updatedCount, deprecatedCount);
    }

    private String generateCatalogVersion(UUID moduleId, Instant now) {
        return moduleId.toString().substring(0, 8) + "-" + now.toEpochMilli();
    }

    private void incrementMetric(String moduleName, String result) {
        incrementMetric(moduleName, result, 1);
    }

    private void incrementMetric(String moduleName, String result, int count) {
        Counter.builder(METRIC_NAME)
                .tag("module", moduleName)
                .tag("result", result)
                .register(meterRegistry)
                .increment(count);
    }

    private record DiffResult(List<Permission> toSave, int addedCount, int updatedCount, int deprecatedCount) {
    }
}
