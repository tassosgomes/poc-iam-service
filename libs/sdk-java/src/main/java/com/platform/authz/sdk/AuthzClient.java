package com.platform.authz.sdk;

import com.platform.authz.sdk.dto.SyncRequest;
import com.platform.authz.sdk.dto.SyncResult;
import java.util.Set;

/**
 * Typed client for the AuthZ Platform.
 *
 * <p>Provides bulk-fetch of user permissions, single-permission check
 * and catalog sync capabilities.
 */
public interface AuthzClient {

    /**
     * Fetches all permissions for the given user via the bulk-fetch endpoint.
     * The runtime call propagates the current user JWT.
     *
     * @param userId the user identifier
     * @return an unmodifiable set of permission codes
     */
    Set<String> fetchUserPermissions(String userId);

    /**
     * Checks whether the given user holds a specific permission.
     * The runtime call propagates the current user JWT.
     *
     * @param userId     the user identifier
     * @param permission the permission code to verify
     * @return {@code true} if the user has the permission
     */
    boolean check(String userId, String permission);

    /**
     * Synchronises the permission catalog with the AuthZ service.
     * Used by task 17.0 (self-registration).
     *
     * @param request the sync payload
     * @return the sync result from the server
     */
    SyncResult sync(SyncRequest request);
}
