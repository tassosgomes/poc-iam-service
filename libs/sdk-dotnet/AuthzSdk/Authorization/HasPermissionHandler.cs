using System.Security.Claims;
using AuthzSdk.Caching;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace AuthzSdk.Authorization;

/// <summary>
/// Evaluates <see cref="HasPermissionRequirement"/> by consulting the request-scoped
/// <see cref="RequestPermissionCache"/>. Deny by default — only calls
/// <c>context.Succeed</c> when the user holds the required permission.
/// </summary>
public sealed class HasPermissionHandler : AuthorizationHandler<HasPermissionRequirement>
{
    private readonly RequestPermissionCache _permissionCache;
    private readonly IHttpContextAccessor _httpContextAccessor;
    private readonly ILogger<HasPermissionHandler> _logger;

    public HasPermissionHandler(
        RequestPermissionCache permissionCache,
        IHttpContextAccessor httpContextAccessor,
        ILogger<HasPermissionHandler> logger)
    {
        _permissionCache = permissionCache ?? throw new ArgumentNullException(nameof(permissionCache));
        _httpContextAccessor = httpContextAccessor ?? throw new ArgumentNullException(nameof(httpContextAccessor));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    }

    protected override async Task HandleRequirementAsync(
        AuthorizationHandlerContext context,
        HasPermissionRequirement requirement)
    {
        var userId = context.User.FindFirstValue(ClaimTypes.NameIdentifier)
                     ?? context.User.FindFirstValue("sub");

        if (string.IsNullOrWhiteSpace(userId))
        {
            LogDecision(userId: null, requirement.Permission, allowed: false);
            return;
        }

        var cancellationToken = _httpContextAccessor.HttpContext?.RequestAborted ?? CancellationToken.None;

        var userPermissions = await _permissionCache
            .GetUserPermissionsAsync(userId, cancellationToken)
            .ConfigureAwait(false);

        var allowed = PermissionMatcher.Matches(userPermissions.Permissions, requirement.Permission);
        LogDecision(userId, requirement.Permission, allowed);

        if (allowed)
        {
            context.Succeed(requirement);
        }
    }

    private void LogDecision(string? userId, string requiredPermission, bool allowed)
    {
        _logger.LogDebug(
            "permission_check user={UserId} required={RequiredPermission} result={Result}",
            userId ?? "anonymous",
            requiredPermission,
            allowed ? "allow" : "deny");
    }
}
