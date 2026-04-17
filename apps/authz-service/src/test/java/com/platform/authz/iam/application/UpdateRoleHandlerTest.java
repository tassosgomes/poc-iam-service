package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RolePermission;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Instant;
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
class UpdateRoleHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-17T18:45:00Z");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private UpdateRoleHandler handler;

    @BeforeEach
    void setUp() {
        RoleManagementSupport roleManagementSupport = new RoleManagementSupport(moduleRepository, permissionRepository);
        handler = new UpdateRoleHandler(roleRepository, roleManagementSupport);
    }

    @Test
    void handle_WhenRepositoryDetectsConcurrentDuplicate_ShouldTranslateConflict() {
        // Arrange
        UUID roleId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        Role existingRole = new Role(
                roleId,
                moduleId,
                "VENDAS_ANALISTA",
                "Sales analyst",
                "user-admin",
                NOW.minusSeconds(3600),
                Set.of(new RolePermission(roleId, permissionId))
        );
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW.minusSeconds(7200), null);
        UpdateRoleCommand command = new UpdateRoleCommand(
                roleId,
                "VENDAS_GERENTE",
                "Sales manager",
                Set.of(permissionId)
        );

        when(roleRepository.findById(roleId)).thenReturn(java.util.Optional.of(existingRole));
        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(permissionRepository.findByIds(Set.of(permissionId))).thenReturn(List.of(
                new Permission(permissionId, moduleId, "vendas.orders.read", "Read orders", PermissionStatus.ACTIVE, null, NOW, NOW)
        ));
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(com.platform.authz.iam.domain.RoleConflictException.class)
                .hasMessage("Role 'VENDAS_GERENTE' already exists for the selected module");
    }
}
