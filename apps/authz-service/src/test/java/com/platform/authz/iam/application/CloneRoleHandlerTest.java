package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CloneRoleHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-17T18:30:00Z");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    private CloneRoleHandler handler;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        RoleManagementSupport roleManagementSupport = new RoleManagementSupport(moduleRepository, permissionRepository);
        handler = new CloneRoleHandler(roleRepository, roleManagementSupport, fixedClock);
    }

    @Test
    void handle_WithoutRequestedName_ShouldCloneRoleWithCopySuffix() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role sourceRole = new Role(
                roleId,
                moduleId,
                "VENDAS_GERENTE",
                "Sales manager",
                "user-admin",
                NOW.minusSeconds(3600),
                Set.of(new com.platform.authz.iam.domain.RolePermission(roleId, permissionId))
        );
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW.minusSeconds(7200), null);

        when(roleRepository.findById(roleId)).thenReturn(java.util.Optional.of(sourceRole));
        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE_COPY")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Role clonedRole = handler.handle(new CloneRoleCommand(roleId, null, "cloner-user"));

        // Assert
        assertThat(clonedRole.id()).isNotEqualTo(roleId);
        assertThat(clonedRole.name()).isEqualTo("VENDAS_GERENTE_COPY");
        assertThat(clonedRole.createdBy()).isEqualTo("cloner-user");
        assertThat(clonedRole.createdAt()).isEqualTo(NOW);
        assertThat(clonedRole.permissionIds()).containsExactly(permissionId);
    }

    @Test
    void handle_WhenCopyNameAlreadyExists_ShouldGenerateSequencedCopyName() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role sourceRole = new Role(
                roleId,
                moduleId,
                "VENDAS_GERENTE",
                "Sales manager",
                "user-admin",
                NOW.minusSeconds(3600),
                Set.of(new com.platform.authz.iam.domain.RolePermission(roleId, permissionId))
        );
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW.minusSeconds(7200), null);

        when(roleRepository.findById(roleId)).thenReturn(java.util.Optional.of(sourceRole));
        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE_COPY")).thenReturn(true);
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE_COPY_2")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Role clonedRole = handler.handle(new CloneRoleCommand(roleId, null, "cloner-user"));

        // Assert
        assertThat(clonedRole.name()).isEqualTo("VENDAS_GERENTE_COPY_2");
    }

    @Test
    void handle_WhenRepositoryDetectsConcurrentDuplicate_ShouldTranslateConflict() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID permissionId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role sourceRole = new Role(
                roleId,
                moduleId,
                "VENDAS_GERENTE",
                "Sales manager",
                "user-admin",
                NOW.minusSeconds(3600),
                Set.of(new com.platform.authz.iam.domain.RolePermission(roleId, permissionId))
        );
        Module module = new Module(moduleId, "Sales", "vendas", "Sales module", "platform-admin", NOW.minusSeconds(7200), null);

        when(roleRepository.findById(roleId)).thenReturn(java.util.Optional.of(sourceRole));
        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(roleRepository.existsByModuleIdAndName(moduleId, "VENDAS_GERENTE_COPY")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new CloneRoleCommand(roleId, null, "cloner-user")))
                .isInstanceOf(com.platform.authz.iam.domain.RoleConflictException.class)
                .hasMessage("Role 'VENDAS_GERENTE_COPY' already exists for the selected module");
    }
}
