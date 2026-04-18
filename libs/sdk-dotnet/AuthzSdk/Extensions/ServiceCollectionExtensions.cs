using System.Net;
using AuthzSdk.Authorization;
using AuthzSdk.Caching;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Http.Resilience;
using Microsoft.Extensions.Options;
using Polly;

namespace AuthzSdk.Extensions;

public static class ServiceCollectionExtensions
{
    public static IServiceCollection AddAuthzSdk(this IServiceCollection services, IConfiguration config)
    {
        ArgumentNullException.ThrowIfNull(services);
        ArgumentNullException.ThrowIfNull(config);

        services.AddHttpContextAccessor();
        services.AddOptions<AuthzOptions>()
            .Bind(config.GetSection(AuthzOptions.SectionName))
            .Validate(static options => HasRequiredConfiguration(options), "Authz configuration is invalid.")
            .ValidateOnStart();

        services.AddHttpClient<IAuthzClient, AuthzClient>(ConfigureHttpClient)
            .AddResilienceHandler("authz-sdk", static builder =>
            {
                var predicate = new PredicateBuilder<HttpResponseMessage>()
                    .Handle<HttpRequestException>()
                    .HandleResult(static response => ShouldHandle(response.StatusCode));

                builder.AddRetry(new HttpRetryStrategyOptions
                {
                    MaxRetryAttempts = 3,
                    Delay = TimeSpan.FromMilliseconds(200),
                    BackoffType = DelayBackoffType.Exponential,
                    UseJitter = true,
                    ShouldHandle = predicate
                });

                builder.AddCircuitBreaker(new HttpCircuitBreakerStrategyOptions
                {
                    FailureRatio = 0.5,
                    MinimumThroughput = 10,
                    SamplingDuration = TimeSpan.FromSeconds(30),
                    BreakDuration = TimeSpan.FromSeconds(10),
                    ShouldHandle = predicate
                });
            });

        services.AddScoped<RequestPermissionCache>();

        services.TryAddSingleton<IAuthorizationPolicyProvider, PermissionPolicyProvider>();
        services.AddScoped<IAuthorizationHandler, HasPermissionHandler>();

        return services;
    }

    private static void ConfigureHttpClient(IServiceProvider serviceProvider, HttpClient client)
    {
        var options = serviceProvider.GetRequiredService<IOptions<AuthzOptions>>().Value;
        client.BaseAddress = new Uri(options.BaseUrl, UriKind.Absolute);
        client.Timeout = options.Timeout;
    }

    private static bool HasRequiredConfiguration(AuthzOptions options)
    {
        return Uri.TryCreate(options.BaseUrl, UriKind.Absolute, out _)
            && !string.IsNullOrWhiteSpace(options.ModuleId)
            && !string.IsNullOrWhiteSpace(options.ModuleKey)
            && options.Timeout > TimeSpan.Zero;
    }

    private static bool ShouldHandle(HttpStatusCode statusCode)
    {
        return statusCode == HttpStatusCode.RequestTimeout
            || statusCode == HttpStatusCode.TooManyRequests
            || (int)statusCode >= 500;
    }
}
