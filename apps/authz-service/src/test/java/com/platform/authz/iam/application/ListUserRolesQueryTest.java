package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.iam.domain.UserNotFoundException;
import com.platform.authz.iam.domain.UserRoleAssignment;
import com.platform.authz.iam.domain.UserRoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ListUserRolesQueryTest {

    private static final Instant NOW = Instant.parse("2026-04-17T18:00:00Z");

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private UserSearchPort userSearchPort;

    @Mock
    private AdminScopeChecker adminScopeChecker;

    private ListUserRolesQuery query;

    @BeforeEach
    void setUp() {
        query = new ListUserRolesQuery(
                userRoleRepository,
                roleRepository,
                moduleRepository,
                userSearchPort,
                adminScopeChecker,
                new ModuleScopeExtractor()
        );
    }

    @Test
    void handle_WithPlatformAdmin_ShouldReturnAllAssignmentsSortedByRoleName() {
        // Arrange
        UUID salesModuleId = UUID.randomUUID();
        UUID stockModuleId = UUID.randomUUID();
        Role salesRole = role(salesModuleId, UUID.randomUUID(), "VENDAS_GERENTE");
        Role stockRole = role(stockModuleId, UUID.randomUUID(), "ESTOQUE_ANALISTA");
        UserRoleAssignment salesAssignment = assignment("user-123", salesRole.id(), NOW.minusSeconds(120));
        UserRoleAssignment stockAssignment = assignment("user-123", stockRole.id(), NOW.minusSeconds(60));
        Authentication authentication = authentication("admin-user", "ROLE_PLATFORM_ADMIN");

        when(userSearchPort.userExists("user-123")).thenReturn(true);
        when(adminScopeChecker.hasManagementAccess(authentication)).thenReturn(true);
        when(userRoleRepository.findActiveByUserId("user-123")).thenReturn(List.of(salesAssignment, stockAssignment));
        when(roleRepository.findByIds(Set.of(salesRole.id(), stockRole.id()))).thenReturn(List.of(salesRole, stockRole));

        // Act
        List<UserRoleView> result = query.handle("user-123", authentication);

        // Assert
        assertThat(result)
                .extracting(UserRoleView::roleName)
                .containsExactly("ESTOQUE_ANALISTA", "VENDAS_GERENTE");
    }

    @Test
    void handle_WithScopedManager_ShouldReturnOnlyRolesFromManageableModules() {
        // Arrange
        UUID salesModuleId = UUID.randomUUID();
        UUID stockModuleId = UUID.randomUUID();
        Role salesRole = role(salesModuleId, UUID.randomUUID(), "VENDAS_GERENTE");
        Role stockRole = role(stockModuleId, UUID.randomUUID(), "ESTOQUE_ANALISTA");
        UserRoleAssignment salesAssignment = assignment("user-123", salesRole.id(), NOW.minusSeconds(120));
        UserRoleAssignment stockAssignment = assignment("user-123", stockRole.id(), NOW.minusSeconds(60));
        Authentication authentication = authentication("sales-manager", "ROLE_VENDAS_USER_MANAGER");

        when(userSearchPort.userExists("user-123")).thenReturn(true);
        when(adminScopeChecker.hasManagementAccess(authentication)).thenReturn(true);
        when(userRoleRepository.findActiveByUserId("user-123")).thenReturn(List.of(salesAssignment, stockAssignment));
        when(roleRepository.findByIds(Set.of(salesRole.id(), stockRole.id()))).thenReturn(List.of(salesRole, stockRole));
        when(moduleRepository.findById(salesModuleId)).thenReturn(Optional.of(module(salesModuleId, "vendas")));
        when(moduleRepository.findById(stockModuleId)).thenReturn(Optional.of(module(stockModuleId, "estoque")));

        // Act
        List<UserRoleView> result = query.handle("user-123", authentication);

        // Assert
        assertThat(result)
                .extracting(UserRoleView::roleName)
                .containsExactly("VENDAS_GERENTE");
    }

    @Test
    void handle_WhenUserDoesNotExist_ShouldThrowUserNotFoundException() {
        // Arrange
        when(userSearchPort.userExists("missing-user")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> query.handle(
                "missing-user",
                authentication("admin-user", "ROLE_PLATFORM_ADMIN")
        )).isInstanceOf(UserNotFoundException.class);
    }

    private Role role(UUID moduleId, UUID roleId, String roleName) {
        return new Role(
                roleId,
                moduleId,
                roleName,
                "Role description",
                "system",
                NOW.minusSeconds(3600),
                Set.of()
        );
    }

    private UserRoleAssignment assignment(String userId, UUID roleId, Instant assignedAt) {
        return new UserRoleAssignment(
                UUID.randomUUID(),
                userId,
                roleId,
                "admin-user",
                assignedAt,
                null,
                null
        );
    }

    private Module module(UUID moduleId, String allowedPrefix) {
        return new Module(
                moduleId,
                allowedPrefix.toUpperCase(),
                allowedPrefix,
                allowedPrefix + " module",
                "system",
                NOW.minusSeconds(3600),
                null
        );
    }

    private Authentication authentication(String subject, String authority) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(subject, "n/a", authority);
        authentication.setAuthenticated(true);
        return authentication;
    }
}
