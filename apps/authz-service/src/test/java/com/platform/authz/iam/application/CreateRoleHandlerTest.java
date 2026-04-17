package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.iam.domain.InvalidRoleNameException;
import com.platform.authz.iam.domain.PermissionModuleMismatchException;
import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreateRoleHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-17T18:00:00Z");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private CreateRoleHandler handler;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        RoleManagementSupport roleManagementSupport = new RoleManagementSupport(moduleRepository, permissionRepository);
        handler = new CreateRoleHandler(roleRepository, roleManagementSupport, fixedClock);
    }

    @Test
    void handle_WithValidCommand_ShouldCreateRole() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW, null);
        Permission permission = Permission.createActive(moduleId, "vendas.orders.read", "Read orders", NOW);
        CreateRoleCommand command = new CreateRoleCommand(
                moduleId,
                "VENDAS_GERENTE",
                "Sales manager",
                Set.of(permissionId),
                "user-admin"
        );

        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(permissionRepository.findByIds(Set.of(permissionId))).thenReturn(List.of(
                new Permission(permissionId, moduleId, permission.code(), permission.description(), PermissionStatus.ACTIVE, null, NOW, NOW)
        ));
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Role result = handler.handle(command);

        // Assert
        assertThat(result.moduleId()).isEqualTo(moduleId);
        assertThat(result.name()).isEqualTo("VENDAS_GERENTE");
        assertThat(result.description()).isEqualTo("Sales manager");
        assertThat(result.createdBy()).isEqualTo("user-admin");
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.permissionIds()).containsExactly(permissionId);
    }

    @Test
    void handle_WithRoleNameWithoutModulePrefix_ShouldThrowInvalidRoleNameException() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW, null);
        CreateRoleCommand command = new CreateRoleCommand(
                moduleId,
                "ESTOQUE_GERENTE",
                "Sales manager",
                Set.of(),
                "user-admin"
        );

        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(InvalidRoleNameException.class);
    }

    @Test
    void handle_WithPermissionFromAnotherModule_ShouldThrowPermissionModuleMismatchException() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID foreignModuleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW, null);
        CreateRoleCommand command = new CreateRoleCommand(
                moduleId,
                "VENDAS_GERENTE",
                "Sales manager",
                Set.of(permissionId),
                "user-admin"
        );

        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(permissionRepository.findByIds(Set.of(permissionId))).thenReturn(List.of(
                new Permission(
                        permissionId,
                        foreignModuleId,
                        "estoque.orders.read",
                        "Read stock orders",
                        PermissionStatus.ACTIVE,
                        null,
                        NOW,
                        NOW
                )
        ));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(PermissionModuleMismatchException.class);
    }

    @Test
    void handle_WhenRepositoryDetectsConcurrentDuplicate_ShouldTranslateConflict() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW, null);
        Permission permission = Permission.createActive(moduleId, "vendas.orders.read", "Read orders", NOW);
        CreateRoleCommand command = new CreateRoleCommand(
                moduleId,
                "VENDAS_GERENTE",
                "Sales manager",
                Set.of(permissionId),
                "user-admin"
        );

        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(permissionRepository.findByIds(Set.of(permissionId))).thenReturn(List.of(
                new Permission(permissionId, moduleId, permission.code(), permission.description(), PermissionStatus.ACTIVE, null, NOW, NOW)
        ));
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(com.platform.authz.iam.domain.RoleConflictException.class)
                .hasMessage("Role 'VENDAS_GERENTE' already exists for the selected module");
    }
}
