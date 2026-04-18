using System.Security.Claims;
using AuthzSdk.Authorization;
using AuthzSdk.Caching;
using AuthzSdk.Models;
using FluentAssertions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
using Moq;
using Xunit;

namespace AuthzSdk.Tests.Authorization;

public sealed class HasPermissionHandlerTests
{
    private readonly Mock<IAuthzClient> _authzClientMock;
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly HasPermissionHandler _handler;

    public HasPermissionHandlerTests()
    {
        _authzClientMock = new Mock<IAuthzClient>();
        var cache = new RequestPermissionCache(_authzClientMock.Object);
        _httpContextAccessor = new HttpContextAccessor
        {
            HttpContext = new DefaultHttpContext()
        };
        var loggerMock = new Mock<ILogger<HasPermissionHandler>>();
        _handler = new HasPermissionHandler(cache, _httpContextAccessor, loggerMock.Object);
    }

    [Fact]
    public async Task HandleRequirementAsync_WhenUserHasRequiredPermission_ShouldSucceed()
    {
        // Arrange
        var requirement = new HasPermissionRequirement("vendas.orders.create");
        var user = CreateAuthenticatedUser("user-1");
        var context = CreateAuthorizationContext(user, requirement);

        _authzClientMock
            .Setup(client => client.GetUserPermissionsAsync("user-1", It.IsAny<CancellationToken>()))
            .ReturnsAsync(CreateUserPermissions("user-1", "vendas.orders.create"));

        // Act
        await InvokeHandlerAsync(context);

        // Assert
        context.HasSucceeded.Should().BeTrue();
    }

    [Fact]
    public async Task HandleRequirementAsync_WhenUserLacksRequiredPermission_ShouldNotSucceed()
    {
        // Arrange
        var requirement = new HasPermissionRequirement("vendas.orders.create");
        var user = CreateAuthenticatedUser("user-1");
        var context = CreateAuthorizationContext(user, requirement);

        _authzClientMock
            .Setup(client => client.GetUserPermissionsAsync("user-1", It.IsAny<CancellationToken>()))
            .ReturnsAsync(CreateUserPermissions("user-1", "vendas.orders.read"));

        // Act
        await InvokeHandlerAsync(context);

        // Assert
        context.HasSucceeded.Should().BeFalse();
    }

    [Fact]
    public async Task HandleRequirementAsync_WhenUserHasWildcardPermission_ShouldSucceed()
    {
        // Arrange
        var requirement = new HasPermissionRequirement("vendas.orders.create");
        var user = CreateAuthenticatedUser("user-1");
        var context = CreateAuthorizationContext(user, requirement);

        _authzClientMock
            .Setup(client => client.GetUserPermissionsAsync("user-1", It.IsAny<CancellationToken>()))
            .ReturnsAsync(CreateUserPermissions("user-1", "vendas.*"));

        // Act
        await InvokeHandlerAsync(context);

        // Assert
        context.HasSucceeded.Should().BeTrue();
    }

    [Fact]
    public async Task HandleRequirementAsync_WhenNoAuthentication_ShouldNotSucceed()
    {
        // Arrange
        var requirement = new HasPermissionRequirement("vendas.orders.create");
        var user = new ClaimsPrincipal(new ClaimsIdentity());
        var context = CreateAuthorizationContext(user, requirement);

        // Act
        await InvokeHandlerAsync(context);

        // Assert
        context.HasSucceeded.Should().BeFalse();
        _authzClientMock.Verify(
            client => client.GetUserPermissionsAsync(It.IsAny<string>(), It.IsAny<CancellationToken>()),
            Times.Never);
    }

    [Fact]
    public async Task HandleRequirementAsync_WhenUserIdentifiedBySub_ShouldSucceed()
    {
        // Arrange
        var requirement = new HasPermissionRequirement("vendas.orders.create");
        var claims = new[] { new Claim("sub", "user-jwt-sub") };
        var identity = new ClaimsIdentity(claims, "Bearer");
        var user = new ClaimsPrincipal(identity);
        var context = CreateAuthorizationContext(user, requirement);

        _authzClientMock
            .Setup(client => client.GetUserPermissionsAsync("user-jwt-sub", It.IsAny<CancellationToken>()))
            .ReturnsAsync(CreateUserPermissions("user-jwt-sub", "vendas.orders.create"));

        // Act
        await InvokeHandlerAsync(context);

        // Assert
        context.HasSucceeded.Should().BeTrue();
    }

    [Fact]
    public async Task HandleRequirementAsync_WhenRequiredWildcardMatchesUserPermission_ShouldSucceed()
    {
        // Arrange
        var requirement = new HasPermissionRequirement("estoque.*");
        var user = CreateAuthenticatedUser("user-1");
        var context = CreateAuthorizationContext(user, requirement);

        _authzClientMock
            .Setup(client => client.GetUserPermissionsAsync("user-1", It.IsAny<CancellationToken>()))
            .ReturnsAsync(CreateUserPermissions("user-1", "estoque.inventory.view"));

        // Act
        await InvokeHandlerAsync(context);

        // Assert
        context.HasSucceeded.Should().BeTrue();
    }

    private static ClaimsPrincipal CreateAuthenticatedUser(string userId)
    {
        var claims = new[] { new Claim(ClaimTypes.NameIdentifier, userId) };
        var identity = new ClaimsIdentity(claims, "Bearer");
        return new ClaimsPrincipal(identity);
    }

    private static AuthorizationHandlerContext CreateAuthorizationContext(
        ClaimsPrincipal user,
        IAuthorizationRequirement requirement)
    {
        return new AuthorizationHandlerContext(
            [requirement],
            user,
            resource: null);
    }

    private async Task InvokeHandlerAsync(AuthorizationHandlerContext context)
    {
        // AuthorizationHandler<T>.HandleRequirementAsync is protected,
        // so we invoke via the public IAuthorizationHandler.HandleAsync.
        await ((IAuthorizationHandler)_handler).HandleAsync(context);
    }

    private static UserPermissions CreateUserPermissions(string userId, params string[] permissions)
    {
        return new UserPermissions(
            userId,
            new HashSet<string>(permissions),
            DateTimeOffset.UtcNow,
            300);
    }
}
