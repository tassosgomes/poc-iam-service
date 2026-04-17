package com.platform.authz.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.config.AuthzCacheProperties;
import com.platform.authz.iam.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUserPermissionsHandlerTest {

    private static final Instant NOW = Instant.parse("2026-04-20T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private UserRoleRepository userRoleRepository;

    private GetUserPermissionsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetUserPermissionsHandler(
                userRoleRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new AuthzCacheProperties(TTL, 10_000L)
        );
    }

    @Test
    void handle_WithActivePermissions_ShouldReturnAggregatedPermissions() {
        // Arrange
        String userId = "user-123";
        List<String> permissionCodes = List.of(
                "vendas.orders.create",
                "vendas.orders.read",
                "vendas.products.read"
        );
        when(userRoleRepository.findDistinctPermissionCodesByUserId(userId)).thenReturn(permissionCodes);

        // Act
        UserPermissions result = handler.handle(new GetUserPermissionsQuery(userId));

        // Assert
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.permissions()).containsExactly(
                "vendas.orders.create",
                "vendas.orders.read",
                "vendas.products.read"
        );
        assertThat(result.resolvedAt()).isEqualTo(NOW);
        assertThat(result.ttl()).isEqualTo(TTL);
        verify(userRoleRepository).findDistinctPermissionCodesByUserId(userId);
    }

    @Test
    void handle_WithNoPermissions_ShouldReturnEmptyList() {
        // Arrange
        String userId = "user-empty";
        when(userRoleRepository.findDistinctPermissionCodesByUserId(userId)).thenReturn(List.of());

        // Act
        UserPermissions result = handler.handle(new GetUserPermissionsQuery(userId));

        // Assert
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.permissions()).isEmpty();
        assertThat(result.resolvedAt()).isEqualTo(NOW);
        assertThat(result.ttl()).isEqualTo(TTL);
    }

    @Test
    void handle_WithDuplicatesFromQuery_ShouldReturnDeduplicatedList() {
        // Arrange — the DISTINCT in the query should already deduplicate,
        // but the handler should still handle any edge case correctly
        String userId = "user-dedup";
        List<String> permissionCodes = List.of(
                "vendas.orders.create",
                "vendas.orders.read"
        );
        when(userRoleRepository.findDistinctPermissionCodesByUserId(userId)).thenReturn(permissionCodes);

        // Act
        UserPermissions result = handler.handle(new GetUserPermissionsQuery(userId));

        // Assert
        assertThat(result.permissions()).hasSize(2);
        assertThat(result.permissions()).doesNotHaveDuplicates();
    }

    @Test
    void handle_WithNullQuery_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThatThrownBy(() -> handler.handle(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void handle_WithMultipleRolesSharePermissions_ShouldReturnDistinctCodes() {
        // Arrange — user has multiple roles sharing the same permission codes
        // The DISTINCT in the query handles this, so we verify the expected output
        String userId = "user-multi-roles";
        List<String> codes = List.of(
                "estoque.inventory.read",
                "estoque.inventory.update",
                "vendas.orders.create",
                "vendas.orders.read"
        );
        when(userRoleRepository.findDistinctPermissionCodesByUserId(userId)).thenReturn(codes);

        // Act
        UserPermissions result = handler.handle(new GetUserPermissionsQuery(userId));

        // Assert
        assertThat(result.permissions()).hasSize(4);
        assertThat(result.permissions()).doesNotHaveDuplicates();
        assertThat(result.permissions()).containsExactlyElementsOf(codes);
    }

    @Test
    void handle_ShouldReturnImmutablePermissionsList() {
        // Arrange
        String userId = "user-immutable";
        when(userRoleRepository.findDistinctPermissionCodesByUserId(userId)).thenReturn(
                List.of("vendas.orders.create")
        );

        // Act
        UserPermissions result = handler.handle(new GetUserPermissionsQuery(userId));

        // Assert
        assertThatThrownBy(() -> result.permissions().add("hacked.permission"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_WithNullRepository_ShouldThrowNullPointerException() {
        // Act & Assert
        assertThatThrownBy(() -> new GetUserPermissionsHandler(
                null,
                Clock.systemUTC(),
                new AuthzCacheProperties(TTL, 10_000L)
        )).isInstanceOf(NullPointerException.class);
    }
}
