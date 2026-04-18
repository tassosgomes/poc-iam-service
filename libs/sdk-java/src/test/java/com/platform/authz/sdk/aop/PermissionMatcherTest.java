package com.platform.authz.sdk.aop;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionMatcherTest {

    private PermissionMatcher permissionMatcher;

    @BeforeEach
    void setUp() {
        permissionMatcher = new PermissionMatcher();
    }

    @Test
    @DisplayName("matches should return true for exact permission")
    void matches_ExactPermission_ReturnsTrue() {
        // Arrange
        Set<String> userPermissions = Set.of("vendas.orders.create");

        // Act
        boolean result = permissionMatcher.matches(userPermissions, "vendas.orders.create");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matches should return true when required permission uses wildcard")
    void matches_RequiredWildcard_ReturnsTrue() {
        // Arrange
        Set<String> userPermissions = Set.of("vendas.orders.create");

        // Act
        boolean result = permissionMatcher.matches(userPermissions, "vendas.*");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matches should return true when user permission uses wildcard")
    void matches_UserWildcard_ReturnsTrue() {
        // Arrange
        Set<String> userPermissions = Set.of("vendas.*");

        // Act
        boolean result = permissionMatcher.matches(userPermissions, "vendas.orders.create");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matches should return false when permissions do not match")
    void matches_NoMatch_ReturnsFalse() {
        // Arrange
        Set<String> userPermissions = Set.of("estoque.orders.create");

        // Act
        boolean result = permissionMatcher.matches(userPermissions, "vendas.orders.create");

        // Assert
        assertThat(result).isFalse();
    }
}
