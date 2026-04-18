namespace AuthzSdk.Models;

public sealed record SyncRequest(
    string ModuleId,
    string SchemaVersion,
    string PayloadHash,
    IReadOnlyCollection<PermissionDeclaration> Permissions);
