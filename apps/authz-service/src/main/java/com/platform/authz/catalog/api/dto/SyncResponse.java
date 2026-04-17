package com.platform.authz.catalog.api.dto;

public record SyncResponse(
        String catalogVersion,
        int added,
        int updated,
        int deprecated,
        boolean changed
) {
}
