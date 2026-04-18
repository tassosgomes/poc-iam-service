using Microsoft.AspNetCore.Authorization;

namespace AuthzSdk.Authorization;

/// <summary>
/// Authorization requirement that carries the required permission string.
/// </summary>
public sealed class HasPermissionRequirement : IAuthorizationRequirement
{
    public HasPermissionRequirement(string permission)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(permission);
        Permission = permission;
    }

    public string Permission { get; }
}
