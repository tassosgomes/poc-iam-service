package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.audit.application.AuditEventPublisher;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.iam.domain.AdminScopeViolationException;
import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.iam.domain.UserNotFoundException;
import com.platform.authz.iam.domain.UserRoleAssignment;
import com.platform.authz.iam.domain.UserRoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class RevokeRoleHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-17T18:00:00Z");

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserSearchPort userSearchPort;

    @Mock
    private AdminScopeChecker adminScopeChecker;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private RevokeRoleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RevokeRoleHandler(
                roleRepository,
                userRoleRepository,
                userSearchPort,
                adminScopeChecker,
                moduleRepository,
                auditEventPublisher,
                new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void handle_WithActiveAssignment_ShouldRevokeAndPublishAuditEvent() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        RevokeRoleCommand command = new RevokeRoleCommand("user-123", roleId, "admin-user", "10.0.0.10");
        Role role = role(moduleId, roleId, "VENDAS_GERENTE");
        Module module = module(moduleId, "vendas");
        UserRoleAssignment assignment = new UserRoleAssignment(
                UUID.randomUUID(),
                "user-123",
                roleId,
                "admin-user",
                NOW.minusSeconds(120),
                null,
                null
        );

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(userSearchPort.userExists("user-123")).thenReturn(true);
        when(userRoleRepository.findActiveByUserIdAndRoleId("user-123", roleId)).thenReturn(Optional.of(assignment));
        when(userRoleRepository.save(any(UserRoleAssignment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        handler.handle(command, authentication("admin-user", "ROLE_PLATFORM_ADMIN"));

        // Assert
        verify(adminScopeChecker).requireScope(any(Authentication.class), any(UUID.class));
        verify(userRoleRepository).save(any(UserRoleAssignment.class));
        verify(auditEventPublisher).publish(auditEventCaptor.capture());
        assertThat(auditEventCaptor.getValue().eventType()).isEqualTo(AuditEventType.ROLE_REVOKED);
        assertThat(auditEventCaptor.getValue().target()).isEqualTo("user-123");
    }

    @Test
    void handle_WhenAssignmentDoesNotExist_ShouldReturnWithoutPublishingAuditEvent() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role role = role(moduleId, roleId, "VENDAS_GERENTE");
        Module module = module(moduleId, "vendas");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(userSearchPort.userExists("user-123")).thenReturn(true);
        when(userRoleRepository.findActiveByUserIdAndRoleId("user-123", roleId)).thenReturn(Optional.empty());

        // Act
        handler.handle(
                new RevokeRoleCommand("user-123", roleId, "admin-user", null),
                authentication("admin-user", "ROLE_PLATFORM_ADMIN")
        );

        // Assert
        verify(userRoleRepository, never()).save(any(UserRoleAssignment.class));
        verify(auditEventPublisher, never()).publish(any(AuditEvent.class));
    }

    @Test
    void handle_WhenUserDoesNotExist_ShouldThrowUserNotFoundException() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role role = role(moduleId, roleId, "VENDAS_GERENTE");
        Module module = module(moduleId, "vendas");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(userSearchPort.userExists("missing-user")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(
                new RevokeRoleCommand("missing-user", roleId, "admin-user", null),
                authentication("admin-user", "ROLE_PLATFORM_ADMIN")
        )).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void handle_WhenAdminLacksModuleScope_ShouldFailBeforeCheckingUserExistence() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Role role = role(moduleId, roleId, "VENDAS_GERENTE");
        Module module = module(moduleId, "vendas");
        Authentication authentication = authentication("stock-manager", "ROLE_ESTOQUE_USER_MANAGER");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        doThrow(new AdminScopeViolationException(moduleId, module.allowedPrefix()))
                .when(adminScopeChecker)
                .requireScope(authentication, moduleId);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(
                new RevokeRoleCommand("user-123", roleId, "stock-manager", null),
                authentication
        )).isInstanceOf(AdminScopeViolationException.class);

        verify(userSearchPort, never()).userExists(anyString());
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
