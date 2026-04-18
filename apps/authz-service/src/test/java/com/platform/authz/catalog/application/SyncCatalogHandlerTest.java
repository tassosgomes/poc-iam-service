package com.platform.authz.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.audit.application.AuditEventPublisher;
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
import com.platform.authz.shared.exception.PrefixViolationException;
import com.platform.authz.shared.security.ModuleContext;
import com.platform.authz.shared.security.PermissionPrefixValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncCatalogHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-20T10:00:00Z");
    private static final UUID MODULE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String MODULE_NAME = "vendas";
    private static final String ALLOWED_PREFIX = "vendas";

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private SyncEventRepository syncEventRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private PermissionPrefixValidator permissionPrefixValidator;
    private SimpleMeterRegistry meterRegistry;
    private SyncCatalogHandler handler;

    @BeforeEach
    void setUp() {
        permissionPrefixValidator = new PermissionPrefixValidator();
        meterRegistry = new SimpleMeterRegistry();
        handler = new SyncCatalogHandler(
                moduleRepository,
                permissionRepository,
                syncEventRepository,
                permissionPrefixValidator,
                auditEventPublisher,
                meterRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ModuleContext moduleContext() {
        return new ModuleContext(MODULE_ID.toString(), ALLOWED_PREFIX, NOW.minus(Duration.ofDays(1)));
    }

    private Module testModule() {
        return new Module(MODULE_ID, MODULE_NAME, ALLOWED_PREFIX, "Vendas module", "admin", NOW.minus(Duration.ofDays(30)), null);
    }

    // --- Prefix validation ---

    @Test
    void handle_WithPermissionOutsideAllowedPrefix_ShouldThrowPrefixViolationException() {
        // Arrange
        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash123",
                List.of(new SyncCatalogCommand.PermissionEntry("estoque.items.read", "Read items"))
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command, moduleContext()))
                .isInstanceOf(PrefixViolationException.class)
                .hasMessageContaining("estoque.items.read")
                .hasMessageContaining("vendas");

        verify(moduleRepository, never()).findByIdForUpdate(any());
        verify(permissionRepository, never()).saveAll(any());
    }

    // --- Module not found ---

    @Test
    void handle_WithNonExistentModule_ShouldThrowModuleNotFoundException() {
        // Arrange
        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash123",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );
        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command, moduleContext()))
                .isInstanceOf(ModuleNotFoundException.class);
    }

    // --- Added permissions ---

    @Test
    void handle_WithNewPermissions_ShouldReturnAddedCountAndChangedTrue() {
        // Arrange
        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-new",
                List.of(
                        new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"),
                        new SyncCatalogCommand.PermissionEntry("vendas.orders.read", "Read orders")
                )
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of());
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert
        assertThat(result.changed()).isTrue();
        assertThat(result.added()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(result.deprecated()).isZero();
        assertThat(result.catalogVersion()).isNotBlank();

        verify(moduleRepository).updateLastHeartbeatAt(eq(MODULE_ID), eq(NOW));
        verify(syncEventRepository).save(any(SyncEvent.class));
        verify(auditEventPublisher).publish(any(AuditEvent.class));

        // Verify metric
        double addedMetric = meterRegistry.counter("authz_catalog_sync_total", "module", MODULE_NAME, "result", "added").count();
        assertThat(addedMetric).isEqualTo(2.0);
    }

    // --- Updated permissions (description change) ---

    @Test
    void handle_WithDescriptionChange_ShouldReturnUpdatedCount() {
        // Arrange
        Permission existing = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.create",
                "Old description", PermissionStatus.ACTIVE, null, NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(5))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-updated",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "New description"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of(existing));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert
        assertThat(result.changed()).isTrue();
        assertThat(result.added()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.deprecated()).isZero();
    }

    // --- Deprecated permissions ---

    @Test
    void handle_WithRemovedPermission_ShouldDeprecateAndSetSunsetAt() {
        // Arrange
        Permission existing = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.create",
                "Create orders", PermissionStatus.ACTIVE, null, NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(5))
        );
        Permission existingToRemove = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.delete",
                "Delete orders", PermissionStatus.ACTIVE, null, NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(5))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-deprecated",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of(existing, existingToRemove));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert
        assertThat(result.changed()).isTrue();
        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.deprecated()).isEqualTo(1);

        // Verify deprecated permission has sunset_at set
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Permission>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionRepository).saveAll(captor.capture());

        List<Permission> saved = captor.getValue();
        Permission deprecatedPermission = saved.stream()
                .filter(p -> p.code().equals("vendas.orders.delete"))
                .findFirst()
                .orElseThrow();
        assertThat(deprecatedPermission.status()).isEqualTo(PermissionStatus.DEPRECATED);
        assertThat(deprecatedPermission.sunsetAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));

        // Verify metric
        double deprecatedMetric = meterRegistry.counter("authz_catalog_sync_total", "module", MODULE_NAME, "result", "deprecated").count();
        assertThat(deprecatedMetric).isEqualTo(1.0);
    }

    // --- Idempotent sync ---

    @Test
    void handle_WithIdenticalPayloadHash_ShouldReturnChangedFalseAndUpdateHeartbeat() {
        // Arrange
        String payloadHash = "same-hash";
        SyncEvent previousEvent = new SyncEvent(
                UUID.randomUUID(), MODULE_ID, payloadHash, "1.0", 2, 2, 0, 0,
                "prev-version", NOW.minus(Duration.ofMinutes(15))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                payloadHash,
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.of(previousEvent));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert
        assertThat(result.changed()).isFalse();
        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.deprecated()).isZero();
        assertThat(result.catalogVersion()).isEqualTo("prev-version");

        // Heartbeat must still be updated
        verify(moduleRepository).updateLastHeartbeatAt(eq(MODULE_ID), eq(NOW));

        // No new SyncEvent should be created for idempotent sync
        verify(syncEventRepository, never()).save(any(SyncEvent.class));
        verify(permissionRepository, never()).saveAll(any());

        // Verify metric
        double idempotentMetric = meterRegistry.counter("authz_catalog_sync_total", "module", MODULE_NAME, "result", "idempotent").count();
        assertThat(idempotentMetric).isEqualTo(1.0);
    }

    // --- Reactivation of deprecated permission ---

    @Test
    void handle_WithReactivatedPermission_ShouldCountAsAddedOnly() {
        // Arrange
        Permission deprecatedPermission = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.create",
                "Create orders", PermissionStatus.DEPRECATED,
                NOW.plus(Duration.ofDays(15)),
                NOW.minus(Duration.ofDays(30)), NOW.minus(Duration.ofDays(1))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-reactivate",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of(deprecatedPermission));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert
        assertThat(result.changed()).isTrue();
        assertThat(result.added()).isEqualTo(1);
        assertThat(result.updated()).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Permission>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionRepository).saveAll(captor.capture());

        Permission reactivated = captor.getValue().getFirst();
        assertThat(reactivated.status()).isEqualTo(PermissionStatus.ACTIVE);
        assertThat(reactivated.sunsetAt()).isNull();
    }

    // --- Fix 1: Reactivation + description change must NOT double-count ---

    @Test
    void handle_WithReactivatedPermissionAndDescriptionChange_ShouldCountAsAddedNotUpdated() {
        // Arrange — deprecated with old description
        Permission deprecatedPermission = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.create",
                "Old description", PermissionStatus.DEPRECATED,
                NOW.plus(Duration.ofDays(15)),
                NOW.minus(Duration.ofDays(30)), NOW.minus(Duration.ofDays(1))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-reactivate-desc",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "New description"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of(deprecatedPermission));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert — each permission must be in EXACTLY one category
        assertThat(result.changed()).isTrue();
        assertThat(result.added()).isEqualTo(1);    // Reactivation counts as added
        assertThat(result.updated()).isZero();       // NOT double-counted as updated
        assertThat(result.deprecated()).isZero();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Permission>> captor = ArgumentCaptor.forClass(List.class);
        verify(permissionRepository).saveAll(captor.capture());

        Permission saved = captor.getValue().getFirst();
        assertThat(saved.status()).isEqualTo(PermissionStatus.ACTIVE);
        assertThat(saved.description()).isEqualTo("New description");
    }

    // --- Fix 2: moduleId body vs auth mismatch ---

    @Test
    void handle_WithModuleIdMismatch_ShouldThrowModuleIdMismatchException() {
        // Arrange — body says module-A, auth says module-B
        SyncCatalogCommand command = new SyncCatalogCommand(
                "22222222-2222-2222-2222-222222222222",
                "1.0",
                "hash-mismatch",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command, moduleContext()))
                .isInstanceOf(ModuleIdMismatchException.class)
                .hasMessageContaining("22222222-2222-2222-2222-222222222222")
                .hasMessageContaining(MODULE_ID.toString());

        verify(moduleRepository, never()).findByIdForUpdate(any());
    }

    // --- Fix 3: schemaVersion persisted in SyncEvent ---

    @Test
    void handle_WithSchemaVersion_ShouldPersistSchemaVersionInSyncEvent() {
        // Arrange
        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "2.0",
                "hash-schema",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of());
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        handler.handle(command, moduleContext());

        // Assert
        ArgumentCaptor<SyncEvent> syncCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventRepository).save(syncCaptor.capture());

        SyncEvent syncEvent = syncCaptor.getValue();
        assertThat(syncEvent.schemaVersion()).isEqualTo("2.0");
        assertThat(syncEvent.moduleId()).isEqualTo(MODULE_ID);
        assertThat(syncEvent.payloadHash()).isEqualTo("hash-schema");
    }

    // --- Fix 4: no_change metric when hash differs but diff is zero ---

    @Test
    void handle_WithDifferentHashButNoDiff_ShouldEmitNoChangeMetricAndReturnChangedTrue() {
        // Arrange — existing permission identical to incoming
        Permission existingPermission = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.create",
                "Create orders", PermissionStatus.ACTIVE, null,
                NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(10))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-no-diff",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of(existingPermission));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert — hash was different so changed=true, but logical diff is zero
        assertThat(result.added()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.deprecated()).isZero();
        assertThat(result.changed()).isTrue();

        // Verify no_change metric was emitted
        double noChangeMetric = meterRegistry.counter("authz_catalog_sync_total", "module", MODULE_NAME, "result", "no_change").count();
        assertThat(noChangeMetric).isEqualTo(1.0);
    }

    // --- Audit event recording ---

    @Test
    void handle_WithChanges_ShouldRecordAuditEventWithSchemaVersion() {
        // Arrange
        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-audit",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of());
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        handler.handle(command, moduleContext());

        // Assert
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventPublisher).publish(auditCaptor.capture());

        AuditEvent auditEvent = auditCaptor.getValue();
        assertThat(auditEvent.eventType()).isEqualTo(AuditEventType.CATALOG_SYNC);
        assertThat(auditEvent.target()).isEqualTo(MODULE_ID.toString());
        assertThat(auditEvent.payload()).containsEntry("moduleName", MODULE_NAME);
        assertThat(auditEvent.payload()).containsEntry("added", 1);
        assertThat(auditEvent.payload()).containsEntry("payloadHash", "hash-audit");
        assertThat(auditEvent.payload()).containsEntry("schemaVersion", "1.0");
    }

    // --- SyncEvent is persisted ---

    @Test
    void handle_WithChanges_ShouldPersistSyncEventWithAllFields() {
        // Arrange
        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-sync-event",
                List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of());
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        handler.handle(command, moduleContext());

        // Assert
        ArgumentCaptor<SyncEvent> syncCaptor = ArgumentCaptor.forClass(SyncEvent.class);
        verify(syncEventRepository).save(syncCaptor.capture());

        SyncEvent syncEvent = syncCaptor.getValue();
        assertThat(syncEvent.moduleId()).isEqualTo(MODULE_ID);
        assertThat(syncEvent.payloadHash()).isEqualTo("hash-sync-event");
        assertThat(syncEvent.schemaVersion()).isEqualTo("1.0");
        assertThat(syncEvent.permissionCount()).isEqualTo(1);
        assertThat(syncEvent.added()).isEqualTo(1);
        assertThat(syncEvent.occurredAt()).isEqualTo(NOW);
    }

    // --- Mixed operations ---

    @Test
    void handle_WithMixedOperations_ShouldCountAddedUpdatedAndDeprecatedCorrectly() {
        // Arrange
        Permission existingUnchanged = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.read",
                "Read orders", PermissionStatus.ACTIVE, null,
                NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(10))
        );
        Permission existingToUpdate = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.create",
                "Old create desc", PermissionStatus.ACTIVE, null,
                NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(10))
        );
        Permission existingToDeprecate = new Permission(
                UUID.randomUUID(), MODULE_ID, "vendas.orders.delete",
                "Delete orders", PermissionStatus.ACTIVE, null,
                NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(10))
        );

        SyncCatalogCommand command = new SyncCatalogCommand(
                MODULE_ID.toString(),
                "1.0",
                "hash-mixed",
                List.of(
                        new SyncCatalogCommand.PermissionEntry("vendas.orders.read", "Read orders"),
                        new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "New create desc"),
                        new SyncCatalogCommand.PermissionEntry("vendas.orders.approve", "Approve orders")
                )
        );

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any()))
                .thenReturn(List.of(existingUnchanged, existingToUpdate, existingToDeprecate));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SyncCatalogResult result = handler.handle(command, moduleContext());

        // Assert
        assertThat(result.changed()).isTrue();
        assertThat(result.added()).isEqualTo(1);     // vendas.orders.approve
        assertThat(result.updated()).isEqualTo(1);   // vendas.orders.create (description changed)
        assertThat(result.deprecated()).isEqualTo(1); // vendas.orders.delete
    }

    // --- Fix 6: Concurrency test ---

    @Test
    void handle_ConcurrentSyncsForSameModule_ShouldNotProduceSideEffects() throws Exception {
        // Arrange — multiple threads calling handle simultaneously
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<SyncCatalogResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        when(moduleRepository.findByIdForUpdate(MODULE_ID)).thenReturn(Optional.of(testModule()));
        when(syncEventRepository.findLatestByModuleId(MODULE_ID)).thenReturn(Optional.empty());
        when(permissionRepository.findByModuleIdAndStatusIn(eq(MODULE_ID), any())).thenReturn(List.of());
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    SyncCatalogCommand command = new SyncCatalogCommand(
                            MODULE_ID.toString(),
                            "1.0",
                            "hash-concurrent-" + index,
                            List.of(new SyncCatalogCommand.PermissionEntry("vendas.orders.create", "Create orders"))
                    );
                    readyLatch.countDown();
                    startLatch.await(5, TimeUnit.SECONDS);
                    SyncCatalogResult result = handler.handle(command, moduleContext());
                    results.add(result);
                } catch (Throwable t) {
                    errors.add(t);
                }
            }));
        }

        // Act — release all threads simultaneously
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Assert — all threads completed without unexpected errors
        assertThat(errors).isEmpty();
        assertThat(results).hasSize(threadCount);

        // Each result should be valid (changed=true, consistent counts)
        for (SyncCatalogResult result : results) {
            assertThat(result.changed()).isTrue();
            assertThat(result.catalogVersion()).isNotBlank();
            assertThat(result.added()).isGreaterThanOrEqualTo(0);
        }
    }
}
