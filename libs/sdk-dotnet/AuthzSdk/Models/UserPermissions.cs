namespace AuthzSdk.Models;

public sealed record UserPermissions(
    string UserId,
    HashSet<string> Permissions,
    DateTimeOffset ResolvedAt,
    long TtlSeconds);
