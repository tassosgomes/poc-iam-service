package com.platform.authz.sdk.cache;

import com.platform.authz.sdk.AuthzClient;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request-scoped permission cache.
 *
 * <p>On the first call for a given {@code userId} within the same HTTP request,
 * a bulk-fetch is performed via {@link AuthzClient#fetchUserPermissions(String)}.
 * Subsequent calls for the same {@code userId} return cached results from memory,
 * avoiding redundant network round-trips.
 *
 * <p>This bean must be registered with {@code @RequestScope} so that the cache
 * is automatically discarded at the end of each HTTP request.
 */
public class RequestScopedPermissionCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestScopedPermissionCache.class);

    private final AuthzClient authzClient;
    private final ConcurrentMap<String, Set<String>> cache = new ConcurrentHashMap<>();

    public RequestScopedPermissionCache(AuthzClient authzClient) {
        this.authzClient = Objects.requireNonNull(authzClient, "authzClient must not be null");
    }

    /**
     * Returns the permissions for the given user, fetching them once per request.
     *
     * @param userId the user identifier
     * @return an unmodifiable set of permission codes
     */
    public Set<String> getPermissions(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return cache.computeIfAbsent(userId, this::loadPermissions);
    }

    /**
     * Checks whether the user has a specific permission using the cached set.
     *
     * @param userId     the user identifier
     * @param permission the permission code to verify
     * @return {@code true} if the user holds the permission
     */
    public boolean hasPermission(String userId, String permission) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        return getPermissions(userId).contains(permission);
    }

    private Set<String> loadPermissions(String userId) {
        LOGGER.debug("Cache miss — bulk-fetching permissions for userId={}", userId);
        return authzClient.fetchUserPermissions(userId);
    }
}
