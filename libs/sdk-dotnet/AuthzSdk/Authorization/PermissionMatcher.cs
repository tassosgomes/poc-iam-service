namespace AuthzSdk.Authorization;

/// <summary>
/// Matches required permissions against user permissions, including wildcard support.
/// Semantically equivalent to the Java PermissionMatcher.
/// </summary>
public static class PermissionMatcher
{
    /// <summary>
    /// Checks whether the user permission set satisfies the required permission.
    /// Supports exact match and wildcard patterns (e.g. "estoque.*").
    /// </summary>
    public static bool Matches(HashSet<string> userPermissions, string requiredPermission)
    {
        ArgumentNullException.ThrowIfNull(userPermissions);
        ArgumentException.ThrowIfNullOrWhiteSpace(requiredPermission);

        if (userPermissions.Contains(requiredPermission))
        {
            return true;
        }

        if (requiredPermission.EndsWith(".*", StringComparison.Ordinal))
        {
            var prefix = requiredPermission[..^2];
            var prefixWithDot = prefix + ".";

            return userPermissions.Any(permission => permission.StartsWith(prefixWithDot, StringComparison.Ordinal));
        }

        foreach (var permission in userPermissions)
        {
            if (!permission.EndsWith(".*", StringComparison.Ordinal))
            {
                continue;
            }

            var prefix = permission[..^2];
            if (requiredPermission.StartsWith(prefix + ".", StringComparison.Ordinal))
            {
                return true;
            }
        }

        return false;
    }
}
