package com.platform.authz.catalog.application;

public record SyncCatalogResult(
        String catalogVersion,
        int added,
        int updated,
        int deprecated,
        boolean changed
) {
}
