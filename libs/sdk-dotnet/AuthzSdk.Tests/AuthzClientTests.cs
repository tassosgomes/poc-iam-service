using AuthzSdk.Caching;
using AuthzSdk.Extensions;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using WireMock;
using WireMock.RequestBuilders;
using WireMock.ResponseBuilders;
using WireMock.Server;
using WireMock.Types;
using WireMock.Util;
using Xunit;

namespace AuthzSdk.Tests;

public sealed class AuthzClientTests : IDisposable
{
    private readonly WireMockServer _server;

    public AuthzClientTests()
    {
        _server = WireMockServer.Start();
    }

    [Fact]
    public async Task GetUserPermissionsAsync_WhenRegisteredViaDi_ShouldPropagateBearerToken()
    {
        // Arrange
        _server
            .Given(Request.Create().WithPath("/v1/users/user-1/permissions").UsingGet())
            .RespondWith(
                Response.Create()
                    .WithStatusCode(200)
                    .WithBodyAsJson(new
                    {
                        userId = "user-1",
                        permissions = new[] { "orders.read", "orders.write" },
                        resolvedAt = DateTimeOffset.UtcNow,
                        ttlSeconds = 300
                    }));

        using var provider = CreateServiceProvider("Bearer runtime-jwt");
        var client = provider.GetRequiredService<IAuthzClient>();

        // Act
        var result = await client.GetUserPermissionsAsync("user-1");

        // Assert
        result.UserId.Should().Be("user-1");
        result.Permissions.Should().NotBeNull();
        result.Permissions!.Should().BeEquivalentTo(["orders.read", "orders.write"]);

        _server.LogEntries.Should().HaveCount(1);
        var runtimeRequest = _server.LogEntries.Single().RequestMessage;
        runtimeRequest.Should().NotBeNull();
        var runtimeHeaders = runtimeRequest!.Headers ?? throw new InvalidOperationException("Headers were not captured.");
        var runtimeAuthorization = runtimeHeaders["Authorization"];
        runtimeAuthorization.Should().NotBeNull();
        runtimeAuthorization!
            .Should()
            .ContainSingle()
            .Which.Should()
            .Be("Bearer runtime-jwt");
    }

    [Fact]
    public async Task GetUserPermissionsAsync_WhenServiceReturns503Twice_ShouldRetryAndSucceed()
    {
        // Arrange
        var attempts = 0;
        _server
            .Given(Request.Create().WithPath("/v1/users/user-1/permissions").UsingGet())
            .RespondWith(
                Response.Create()
                    .WithCallback(_ =>
                    {
                        attempts++;

                        if (attempts < 3)
                        {
                            return new ResponseMessage
                            {
                                StatusCode = 503
                            };
                        }

                        return new ResponseMessage
                        {
                            StatusCode = 200,
                            BodyData = new BodyData
                            {
                                DetectedBodyType = BodyType.Json,
                                BodyAsJson = new
                                {
                                    userId = "user-1",
                                    permissions = new[] { "orders.read" },
                                    resolvedAt = DateTimeOffset.UtcNow,
                                    ttlSeconds = 300
                                }
                            }
                        };
                    }));

        using var provider = CreateServiceProvider("Bearer runtime-jwt");
        var client = provider.GetRequiredService<IAuthzClient>();

        // Act
        var result = await client.GetUserPermissionsAsync("user-1");

        // Assert
        result.Permissions.Should().NotBeNull();
        result.Permissions!.Should().ContainSingle().Which.Should().Be("orders.read");
        _server.LogEntries.Should().HaveCount(3);
    }

    [Fact]
    public async Task GetUserPermissionsAsync_WhenUnauthorized_ShouldThrowAuthzClientExceptionWithoutRetry()
    {
        // Arrange
        _server
            .Given(Request.Create().WithPath("/v1/users/user-1/permissions").UsingGet())
            .RespondWith(
                Response.Create()
                    .WithStatusCode(401)
                    .WithBody("""{"title":"Unauthorized"}"""));

        using var provider = CreateServiceProvider("Bearer runtime-jwt");
        var client = provider.GetRequiredService<IAuthzClient>();

        // Act
        var action = async () => await client.GetUserPermissionsAsync("user-1");

        // Assert
        await action.Should().ThrowAsync<Exceptions.AuthzClientException>()
            .Where(exception => exception.StatusCode == StatusCodes.Status401Unauthorized)
            ;

        _server.LogEntries.Should().HaveCount(1);
    }

    [Fact]
    public async Task SyncAsync_WhenRegisteredViaDi_ShouldUseModuleCredentials()
    {
        // Arrange
        _server
            .Given(Request.Create().WithPath("/v1/catalog/sync").UsingPost())
            .RespondWith(
                Response.Create()
                    .WithStatusCode(200)
                    .WithBodyAsJson(new
                    {
                        catalogVersion = "catalog-1",
                        added = 1,
                        updated = 0,
                        deprecated = 0,
                        changed = true
                    }));

        using var provider = CreateServiceProvider("Bearer runtime-jwt");
        var client = provider.GetRequiredService<IAuthzClient>();

        // Act
        var result = await client.SyncAsync(new Models.SyncRequest(
                "module-1",
                "1.0.0",
                "hash-1",
                [new Models.PermissionDeclaration("orders.read", "Read orders")]))
            ;

        // Assert
        result.CatalogVersion.Should().Be("catalog-1");
        _server.LogEntries.Should().HaveCount(1);

        var request = _server.LogEntries.Single().RequestMessage;
        request.Should().NotBeNull();
        var syncHeaders = request!.Headers ?? throw new InvalidOperationException("Headers were not captured.");
        var syncAuthorization = syncHeaders["Authorization"];
        var moduleId = syncHeaders["X-Module-Id"];
        syncAuthorization.Should().NotBeNull();
        moduleId.Should().NotBeNull();
        syncAuthorization!.Should().ContainSingle().Which.Should().Be("Bearer module-key");
        moduleId!.Should().ContainSingle().Which.Should().Be("module-1");
    }

    public void Dispose()
    {
        _server.Dispose();
    }

    private ServiceProvider CreateServiceProvider(string authorizationHeader)
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                [$"{AuthzOptions.SectionName}:BaseUrl"] = _server.Urls[0],
                [$"{AuthzOptions.SectionName}:ModuleId"] = "module-1",
                [$"{AuthzOptions.SectionName}:ModuleKey"] = "module-key",
                [$"{AuthzOptions.SectionName}:Timeout"] = "00:00:05"
            })
            .Build();

        var services = new ServiceCollection();
        services.AddLogging();
        services.AddAuthzSdk(configuration);

        var provider = services.BuildServiceProvider(new ServiceProviderOptions
        {
            ValidateOnBuild = true,
            ValidateScopes = true
        });

        var httpContextAccessor = provider.GetRequiredService<IHttpContextAccessor>();
        httpContextAccessor.HttpContext = new DefaultHttpContext();
        httpContextAccessor.HttpContext.Request.Headers.Authorization = authorizationHeader;

        return provider;
    }
}
