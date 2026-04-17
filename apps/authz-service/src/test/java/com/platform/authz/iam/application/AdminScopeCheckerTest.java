package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.platform.authz.iam.domain.AdminScopeViolationException;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AdminScopeCheckerTest {

    @Mock
    private ModuleRepository moduleRepository;

    private AdminScopeChecker adminScopeChecker;

    @BeforeEach
    void setUp() {
        adminScopeChecker = new AdminScopeChecker(moduleRepository, new ModuleScopeExtractor());
    }

    @Test
    void requireScope_WithPlatformAdmin_ShouldAllowAccess() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, "vendas")));

        // Act & Assert
        assertThatCode(() -> adminScopeChecker.requireScope(authentication("user-admin", "ROLE_PLATFORM_ADMIN"), moduleId))
                .doesNotThrowAnyException();
    }

    @Test
    void requireScope_WithScopedManagerFromSameModule_ShouldAllowAccess() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, "vendas")));

        // Act & Assert
        assertThatCode(() -> adminScopeChecker.requireScope(authentication("sales-manager", "ROLE_VENDAS_USER_MANAGER"), moduleId))
                .doesNotThrowAnyException();
    }

    @Test
    void requireScope_WithScopedManagerFromAnotherModule_ShouldThrowAdminScopeViolationException() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, "vendas")));

        // Act & Assert
        assertThatThrownBy(() -> adminScopeChecker.requireScope(authentication("stock-manager", "ROLE_ESTOQUE_USER_MANAGER"), moduleId))
                .isInstanceOf(AdminScopeViolationException.class)
                .hasMessageContaining("vendas");
    }

    private Module module(UUID moduleId, String allowedPrefix) {
        return new Module(
                moduleId,
                allowedPrefix.toUpperCase(),
                allowedPrefix,
                allowedPrefix + " module",
                "system",
                Instant.parse("2026-04-17T18:00:00Z"),
                null
        );
    }

    private Authentication authentication(String subject, String authority) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(subject, "n/a", authority);
        authentication.setAuthenticated(true);
        return authentication;
    }
}
