package com.platform.authz.sdk.dto;

/**
 * Response DTO for the catalog sync endpoint.
 * Maps to {@code POST /v1/catalog/sync}.
 */
public record SyncResult(
        String catalogVersion,
        int added,
        int updated,
        int deprecated,
        boolean changed
) {
}
