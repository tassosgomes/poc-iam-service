using AuthzSdk.Authorization;
using FluentAssertions;
using Xunit;

namespace AuthzSdk.Tests.Authorization;

public sealed class PermissionMatcherTests
{
    [Fact]
    public void Matches_WhenExactPermission_ShouldReturnTrue()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "vendas.orders.create" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.orders.create");

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public void Matches_WhenRequiredPermissionUsesWildcard_ShouldReturnTrue()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "vendas.orders.create" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.*");

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public void Matches_WhenUserPermissionUsesWildcard_ShouldReturnTrue()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "vendas.*" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.orders.create");

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public void Matches_WhenPermissionsDoNotMatch_ShouldReturnFalse()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "estoque.orders.create" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.orders.create");

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void Matches_WhenUserHasNoPermissions_ShouldReturnFalse()
    {
        // Arrange
        var userPermissions = new HashSet<string>();

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.orders.create");

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void Matches_WhenWildcardPrefixDoesNotMatch_ShouldReturnFalse()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "estoque.*" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.orders.create");

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void Matches_WhenRequiredWildcardDoesNotMatchAnyUser_ShouldReturnFalse()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "estoque.inventory.view" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "vendas.*");

        // Assert
        result.Should().BeFalse();
    }

    [Fact]
    public void Matches_WhenMultipleUserPermissionsOneWildcardMatches_ShouldReturnTrue()
    {
        // Arrange
        var userPermissions = new HashSet<string> { "rh.employees.view", "estoque.*" };

        // Act
        var result = PermissionMatcher.Matches(userPermissions, "estoque.inventory.view");

        // Assert
        result.Should().BeTrue();
    }

    [Fact]
    public void Matches_WhenNullUserPermissions_ShouldThrow()
    {
        // Act
        var action = () => PermissionMatcher.Matches(null!, "vendas.orders.create");

        // Assert
        action.Should().Throw<ArgumentNullException>();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("  ")]
    public void Matches_WhenInvalidRequiredPermission_ShouldThrow(string? requiredPermission)
    {
        // Arrange
        var userPermissions = new HashSet<string> { "vendas.orders.create" };

        // Act
        var action = () => PermissionMatcher.Matches(userPermissions, requiredPermission!);

        // Assert
        action.Should().Throw<ArgumentException>();
    }
}
