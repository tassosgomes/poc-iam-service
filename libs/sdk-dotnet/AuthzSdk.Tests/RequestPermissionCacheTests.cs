using AuthzSdk.Caching;
using AuthzSdk.Extensions;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using WireMock.RequestBuilders;
using WireMock.ResponseBuilders;
using WireMock.Server;
using Xunit;

namespace AuthzSdk.Tests;

public sealed class RequestPermissionCacheTests : IDisposable
{
    private readonly WireMockServer _server;

    public RequestPermissionCacheTests()
    {
        _server = WireMockServer.Start();
    }

    [Fact]
    public async Task GetUserPermissionsAsync_WhenCalledTwiceInSameScope_ShouldPerformSingleHttpCall()
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

        using var provider = CreateServiceProvider();
        using var scope = provider.CreateScope();
        var cache = scope.ServiceProvider.GetRequiredService<RequestPermissionCache>();

        // Act
        var first = await cache.GetUserPermissionsAsync("user-1");
        var second = await cache.GetUserPermissionsAsync("user-1");

        // Assert
        first.Should().BeEquivalentTo(second);
        _server.LogEntries.Should().HaveCount(1);
    }

    [Fact]
    public async Task HasPermissionAsync_WhenScopeChanges_ShouldFetchAgain()
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
                        permissions = new[] { "orders.read" },
                        resolvedAt = DateTimeOffset.UtcNow,
                        ttlSeconds = 300
                    }));

        using var provider = CreateServiceProvider();

        // Act
        using (var firstScope = provider.CreateScope())
        {
            var firstCache = firstScope.ServiceProvider.GetRequiredService<RequestPermissionCache>();
            (await firstCache.HasPermissionAsync("user-1", "orders.read")).Should().BeTrue();
        }

        using (var secondScope = provider.CreateScope())
        {
            var secondCache = secondScope.ServiceProvider.GetRequiredService<RequestPermissionCache>();
            (await secondCache.HasPermissionAsync("user-1", "orders.read")).Should().BeTrue();
        }

        // Assert
        _server.LogEntries.Should().HaveCount(2);
    }

    public void Dispose()
    {
        _server.Dispose();
    }

    private ServiceProvider CreateServiceProvider()
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
        httpContextAccessor.HttpContext.Request.Headers.Authorization = "Bearer runtime-jwt";

        return provider;
    }
}
