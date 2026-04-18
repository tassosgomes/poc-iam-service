namespace AuthzSdk.Models;

public sealed record SyncResult(
    string CatalogVersion,
    int Added,
    int Updated,
    int Deprecated,
    bool Changed);
