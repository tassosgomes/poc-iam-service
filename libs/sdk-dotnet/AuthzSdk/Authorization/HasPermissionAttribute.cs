using Microsoft.AspNetCore.Authorization;

namespace AuthzSdk.Authorization;

/// <summary>
/// Declarative permission guard for ASP.NET Core controllers and actions.
/// Maps the permission string to a dynamic authorization policy resolved by <see cref="PermissionPolicyProvider"/>.
/// </summary>
[AttributeUsage(AttributeTargets.Class | AttributeTargets.Method, AllowMultiple = false, Inherited = true)]
public sealed class HasPermissionAttribute : AuthorizeAttribute
{
    internal const string PolicyPrefix = "Permission:";

    public HasPermissionAttribute(string permission)
        : base($"{PolicyPrefix}{permission}")
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(permission);
    }
}
