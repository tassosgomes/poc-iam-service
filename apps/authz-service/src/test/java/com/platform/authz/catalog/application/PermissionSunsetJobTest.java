package com.platform.authz.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
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
class PermissionSunsetJobTest {
    private static final Instant NOW = Instant.parse("2026-04-25T03:00:00Z");
    private static final UUID MODULE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private PermissionRepository permissionRepository;

    private PermissionSunsetJob permissionSunsetJob;

    @BeforeEach
    void setUp() {
        permissionSunsetJob = new PermissionSunsetJob(
                permissionRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void run_WhenDeprecatedPermissionSunsetHasPassed_ShouldMarkPermissionAsRemoved() {
        // Arrange
        Permission permission = new Permission(
                UUID.randomUUID(),
                MODULE_ID,
                "sales.orders.delete",
                "Delete orders",
                PermissionStatus.DEPRECATED,
                NOW.minus(Duration.ofDays(1)),
                NOW.minus(Duration.ofDays(30)),
                NOW.minus(Duration.ofDays(2))
        );

        when(permissionRepository.findByStatusAndSunsetAtBefore(PermissionStatus.DEPRECATED, NOW))
                .thenReturn(List.of(permission));
        when(permissionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<Permission>> permissionsCaptor = ArgumentCaptor.forClass(List.class);

        // Act
        permissionSunsetJob.run();

        // Assert
        verify(permissionRepository).saveAll(permissionsCaptor.capture());
        List<Permission> savedPermissions = permissionsCaptor.getValue();
        assertThat(savedPermissions).hasSize(1);
        assertThat(savedPermissions.getFirst().status()).isEqualTo(PermissionStatus.REMOVED);
        assertThat(savedPermissions.getFirst().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void run_WhenNoPermissionReachedSunset_ShouldSkipPersistence() {
        // Arrange
        when(permissionRepository.findByStatusAndSunsetAtBefore(PermissionStatus.DEPRECATED, NOW))
                .thenReturn(List.of());

        // Act
        permissionSunsetJob.run();

        // Assert
        verify(permissionRepository, never()).saveAll(any());
    }
}
