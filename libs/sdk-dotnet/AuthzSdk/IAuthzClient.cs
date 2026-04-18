using AuthzSdk.Models;

namespace AuthzSdk;

public interface IAuthzClient
{
    Task<UserPermissions> GetUserPermissionsAsync(string userId, CancellationToken cancellationToken = default);

    Task<bool> CheckPermissionAsync(string userId, string permission, CancellationToken cancellationToken = default);

    Task<SyncResult> SyncAsync(SyncRequest request, CancellationToken cancellationToken = default);
}
