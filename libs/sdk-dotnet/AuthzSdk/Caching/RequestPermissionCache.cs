using System.Collections.Concurrent;
using AuthzSdk.Models;

namespace AuthzSdk.Caching;

public sealed class RequestPermissionCache
{
    private readonly IAuthzClient _authzClient;
    private readonly ConcurrentDictionary<string, Lazy<Task<UserPermissions>>> _cache = new(StringComparer.Ordinal);

    public RequestPermissionCache(IAuthzClient authzClient)
    {
        _authzClient = authzClient ?? throw new ArgumentNullException(nameof(authzClient));
    }

    public async Task<UserPermissions> GetUserPermissionsAsync(string userId, CancellationToken cancellationToken = default)
    {
        ValidateText(userId, nameof(userId));

        var lazyPermissions = _cache.GetOrAdd(
            userId,
            key => new Lazy<Task<UserPermissions>>(
                () => _authzClient.GetUserPermissionsAsync(key, cancellationToken),
                LazyThreadSafetyMode.ExecutionAndPublication));

        try
        {
            return await lazyPermissions.Value.ConfigureAwait(false);
        }
        catch
        {
            _cache.TryRemove(userId, out _);
            throw;
        }
    }

    public async Task<bool> HasPermissionAsync(string userId, string permission, CancellationToken cancellationToken = default)
    {
        ValidateText(userId, nameof(userId));
        ValidateText(permission, nameof(permission));

        var userPermissions = await GetUserPermissionsAsync(userId, cancellationToken).ConfigureAwait(false);
        return userPermissions.Permissions.Contains(permission);
    }

    private static void ValidateText(string value, string paramName)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            throw new ArgumentException("Value cannot be null or whitespace.", paramName);
        }
    }
}
