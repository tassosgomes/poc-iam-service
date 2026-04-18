package com.platform.authz.sdk.cache;

import com.platform.authz.sdk.AuthzClient;
import com.platform.authz.sdk.dto.SyncRequest;
import com.platform.authz.sdk.dto.SyncResult;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestScopedPermissionCacheTest {

    @Mock
    private AuthzClient authzClient;

    private RequestScopedPermissionCache cache;

    @BeforeEach
    void setUp() {
        cache = new RequestScopedPermissionCache(authzClient);
    }

    @Test
    @DisplayName("should fetch permissions once and cache for subsequent calls")
    void getPermissions_MultipleCalls_FetchesOnlyOnce() {
        // Arrange
        Set<String> permissions = Set.of("orders.read", "orders.write");
        when(authzClient.fetchUserPermissions("user-1")).thenReturn(permissions);

        // Act
        Set<String> first = cache.getPermissions("user-1");
        Set<String> second = cache.getPermissions("user-1");
        Set<String> third = cache.getPermissions("user-1");

        // Assert
        assertThat(first).isEqualTo(permissions);
        assertThat(second).isSameAs(first);
        assertThat(third).isSameAs(first);
        verify(authzClient, times(1)).fetchUserPermissions("user-1");
    }

    @Test
    @DisplayName("should fetch separately for different users")
    void getPermissions_DifferentUsers_FetchesEachOnce() {
        // Arrange
        Set<String> permsUser1 = Set.of("orders.read");
        Set<String> permsUser2 = Set.of("products.read");
        when(authzClient.fetchUserPermissions("user-1")).thenReturn(permsUser1);
        when(authzClient.fetchUserPermissions("user-2")).thenReturn(permsUser2);

        // Act
        Set<String> result1 = cache.getPermissions("user-1");
        Set<String> result2 = cache.getPermissions("user-2");

        // Assert
        assertThat(result1).containsExactly("orders.read");
        assertThat(result2).containsExactly("products.read");
        verify(authzClient, times(1)).fetchUserPermissions("user-1");
        verify(authzClient, times(1)).fetchUserPermissions("user-2");
    }

    @Test
    @DisplayName("hasPermission should return true when permission is in cached set")
    void hasPermission_ExistingPermission_ReturnsTrue() {
        // Arrange
        when(authzClient.fetchUserPermissions("user-1"))
                .thenReturn(Set.of("orders.read", "orders.write"));

        // Act
        boolean result = cache.hasPermission("user-1", "orders.read");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("hasPermission should return false when permission is not in cached set")
    void hasPermission_MissingPermission_ReturnsFalse() {
        // Arrange
        when(authzClient.fetchUserPermissions("user-1"))
                .thenReturn(Set.of("orders.read"));

        // Act
        boolean result = cache.hasPermission("user-1", "orders.delete");

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hasPermission should use cache — no extra HTTP call")
    void hasPermission_AfterGetPermissions_UsesCachedData() {
        // Arrange
        when(authzClient.fetchUserPermissions("user-1"))
                .thenReturn(Set.of("orders.read", "orders.write"));

        // Act
        cache.getPermissions("user-1");
        boolean hasRead = cache.hasPermission("user-1", "orders.read");
        boolean hasDelete = cache.hasPermission("user-1", "orders.delete");

        // Assert
        assertThat(hasRead).isTrue();
        assertThat(hasDelete).isFalse();
        verify(authzClient, times(1)).fetchUserPermissions("user-1");
    }
}
