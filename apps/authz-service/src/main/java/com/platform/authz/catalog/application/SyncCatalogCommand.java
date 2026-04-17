package com.platform.authz.catalog.application;

import java.util.List;

public record SyncCatalogCommand(
        String moduleId,
        String schemaVersion,
        String payloadHash,
        List<PermissionEntry> permissions
) {

    public record PermissionEntry(String code, String description) {
    }
}
