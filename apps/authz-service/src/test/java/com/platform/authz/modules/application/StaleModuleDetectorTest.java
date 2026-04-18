package com.platform.authz.modules.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.platform.authz.config.AuthzLifecycleProperties;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaleModuleDetectorTest {
    private static final Instant NOW = Instant.parse("2026-04-25T10:00:00Z");
    private static final Duration STALE_AFTER = Duration.ofDays(7);
    private static final UUID MODULE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private StaleModuleDetector staleModuleDetector;

    @BeforeEach
    void setUp() {
        staleModuleDetector = new StaleModuleDetector(
                moduleRepository,
                permissionRepository,
                auditEventPublisher,
                new AuthzLifecycleProperties(STALE_AFTER, "0 0 3 * * *", Duration.ofHours(1)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void run_WhenModuleHeartbeatIsStale_ShouldMarkActivePermissionsAsStaleAndPublishAuditEvent() {
        // Arrange
        Module staleModule = new Module(
                MODULE_ID,
                "sales",
                "sales",
                "Sales module",
                "admin",
                NOW.minus(Duration.ofDays(30)),
                NOW.minus(Duration.ofDays(8))
        );
        Permission permission = new Permission(
                UUID.randomUUID(),
                MODULE_ID,
                "sales.orders.read",
                "Read orders",
                PermissionStatus.ACTIVE,
                null,
                NOW.minus(Duration.ofDays(20)),
                NOW.minus(Duration.ofDays(1))
        );

        when(moduleRepository.findByLastHeartbeatAtBefore(NOW.minus(STALE_AFTER))).thenReturn(List.of(staleModule));
        when(permissionRepository.findByModuleIdAndStatusIn(MODULE_ID, List.of(PermissionStatus.ACTIVE)))
                .thenReturn(List.of(permission));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<Permission>> permissionsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        staleModuleDetector.run();

        // Assert
        verify(permissionRepository).saveAll(permissionsCaptor.capture());
        verify(auditEventPublisher).publish(auditEventCaptor.capture());

        List<Permission> savedPermissions = permissionsCaptor.getValue();
        assertThat(savedPermissions).hasSize(1);
        assertThat(savedPermissions.getFirst().status()).isEqualTo(PermissionStatus.STALE);
        assertThat(savedPermissions.getFirst().updatedAt()).isEqualTo(NOW);

        AuditEvent auditEvent = auditEventCaptor.getValue();
        assertThat(auditEvent.eventType()).isEqualTo(AuditEventType.MODULE_WENT_STALE);
        assertThat(auditEvent.actorId()).isNull();
        assertThat(auditEvent.target()).isEqualTo(MODULE_ID.toString());
        assertThat(auditEvent.payload()).containsEntry("moduleId", MODULE_ID.toString());
        assertThat(auditEvent.payload()).containsEntry("moduleName", "sales");
        assertThat(auditEvent.payload()).containsEntry("markedPermissions", 1);
        assertThat(auditEvent.payload()).containsKey("payloadHash");
    }

    @Test
    void run_WhenStaleModuleHasNoActivePermissions_ShouldSkipAuditPublishing() {
        // Arrange
        Module staleModule = new Module(
                MODULE_ID,
                "sales",
                "sales",
                "Sales module",
                "admin",
                NOW.minus(Duration.ofDays(30)),
                NOW.minus(Duration.ofDays(8))
        );

        when(moduleRepository.findByLastHeartbeatAtBefore(NOW.minus(STALE_AFTER))).thenReturn(List.of(staleModule));
        when(permissionRepository.findByModuleIdAndStatusIn(MODULE_ID, List.of(PermissionStatus.ACTIVE)))
                .thenReturn(List.of());

        // Act
        staleModuleDetector.run();

        // Assert
        verify(permissionRepository, never()).saveAll(any());
        verify(auditEventPublisher, never()).publish(any(AuditEvent.class));
    }
}
