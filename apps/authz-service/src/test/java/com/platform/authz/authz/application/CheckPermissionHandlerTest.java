package com.platform.authz.authz.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.iam.application.GetUserPermissionsHandler;
import com.platform.authz.iam.application.GetUserPermissionsQuery;
import com.platform.authz.iam.application.UserPermissions;
import com.platform.authz.iam.domain.UserRoleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckPermissionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-20T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String USER_ID = "user-123";

    @Mock
    private GetUserPermissionsHandler getUserPermissionsHandler;

    @Mock
    private UserRoleRepository userRoleRepository;

    private CheckPermissionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CheckPermissionHandler(getUserPermissionsHandler, userRoleRepository);
    }

    @Test
    void handle_WithActivePermission_ShouldReturnAllowedWithActiveSource() {
        // Arrange
        String permission = "vendas.orders.create";
        UserPermissions permissions = createPermissions(USER_ID, Set.of(permission, "vendas.orders.read"));
        when(getUserPermissionsHandler.handle(new GetUserPermissionsQuery(USER_ID))).thenReturn(permissions);
        when(userRoleRepository.findPermissionStatusByUserIdAndCode(USER_ID, permission))
                .thenReturn(Optional.of("ACTIVE"));

        // Act
        CheckPermissionResult result = handler.handle(new CheckPermissionQuery(USER_ID, permission));

        // Assert
        assertThat(result.allowed()).isTrue();
        assertThat(result.source()).isEqualTo("active");
        verify(getUserPermissionsHandler).handle(new GetUserPermissionsQuery(USER_ID));
        verify(userRoleRepository).findPermissionStatusByUserIdAndCode(USER_ID, permission);
    }

    @Test
    void handle_WithDeprecatedPermission_ShouldReturnAllowedWithDeprecatedSource() {
        // Arrange
        String permission = "vendas.orders.create";
        UserPermissions permissions = createPermissions(USER_ID, Set.of(permission));
        when(getUserPermissionsHandler.handle(new GetUserPermissionsQuery(USER_ID))).thenReturn(permissions);
        when(userRoleRepository.findPermissionStatusByUserIdAndCode(USER_ID, permission))
                .thenReturn(Optional.of("DEPRECATED"));

        // Act
        CheckPermissionResult result = handler.handle(new CheckPermissionQuery(USER_ID, permission));

        // Assert
        assertThat(result.allowed()).isTrue();
        assertThat(result.source()).isEqualTo("deprecated");
    }

    @Test
    void handle_WithDeniedPermission_ShouldReturnDenied() {
        // Arrange
        String permission = "vendas.orders.delete";
        UserPermissions permissions = createPermissions(USER_ID, Set.of("vendas.orders.create"));
        when(getUserPermissionsHandler.handle(new GetUserPermissionsQuery(USER_ID))).thenReturn(permissions);

        // Act
        CheckPermissionResult result = handler.handle(new CheckPermissionQuery(USER_ID, permission));

        // Assert
        assertThat(result.allowed()).isFalse();
        assertThat(result.source()).isEqualTo("denied");
        verify(userRoleRepository, never()).findPermissionStatusByUserIdAndCode(any(), any());
    }

    @Test
    void handle_WithNoPermissions_ShouldReturnDenied() {
        // Arrange
        String permission = "vendas.orders.create";
        UserPermissions permissions = createPermissions(USER_ID, Set.of());
        when(getUserPermissionsHandler.handle(new GetUserPermissionsQuery(USER_ID))).thenReturn(permissions);

        // Act
        CheckPermissionResult result = handler.handle(new CheckPermissionQuery(USER_ID, permission));

        // Assert
        assertThat(result.allowed()).isFalse();
        assertThat(result.source()).isEqualTo("denied");
    }

    @Test
    void handle_WithStatusNotFound_ShouldDefaultToActive() {
        // Arrange — status query returns empty (edge case: cached set has the permission
        // but the status query doesn't find it, e.g. due to data race / eviction)
        String permission = "vendas.orders.create";
        UserPermissions permissions = createPermissions(USER_ID, Set.of(permission));
        when(getUserPermissionsHandler.handle(new GetUserPermissionsQuery(USER_ID))).thenReturn(permissions);
        when(userRoleRepository.findPermissionStatusByUserIdAndCode(USER_ID, permission))
                .thenReturn(Optional.empty());

        // Act
        CheckPermissionResult result = handler.handle(new CheckPermissionQuery(USER_ID, permission));

        // Assert
        assertThat(result.allowed()).isTrue();
        assertThat(result.source()).isEqualTo("active");
    }

    @Test
    void handle_WithNullQuery_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_WithNullGetUserPermissionsHandler_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThatThrownBy(() -> new CheckPermissionHandler(null, userRoleRepository))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("getUserPermissionsHandler");
    }

    @Test
    void constructor_WithNullUserRoleRepository_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThatThrownBy(() -> new CheckPermissionHandler(getUserPermissionsHandler, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userRoleRepository");
    }

    @Test
    void handle_ShouldReuseGetUserPermissionsHandlerForCacheBenefit() {
        // Arrange — verify that the handler delegates to GetUserPermissionsHandler,
        // which is @Cacheable, ensuring cache reuse
        String permission = "vendas.orders.read";
        UserPermissions permissions = createPermissions(USER_ID, Set.of(permission));
        when(getUserPermissionsHandler.handle(new GetUserPermissionsQuery(USER_ID))).thenReturn(permissions);
        when(userRoleRepository.findPermissionStatusByUserIdAndCode(USER_ID, permission))
                .thenReturn(Optional.of("ACTIVE"));

        // Act
        handler.handle(new CheckPermissionQuery(USER_ID, permission));
        handler.handle(new CheckPermissionQuery(USER_ID, permission));

        // Assert — GetUserPermissionsHandler called twice (caching is transparent at this level)
        verify(getUserPermissionsHandler, org.mockito.Mockito.times(2))
                .handle(new GetUserPermissionsQuery(USER_ID));
    }

    private UserPermissions createPermissions(String userId, Set<String> permissionCodes) {
        return new UserPermissions(userId, new LinkedHashSet<>(permissionCodes), NOW, TTL);
    }
}
