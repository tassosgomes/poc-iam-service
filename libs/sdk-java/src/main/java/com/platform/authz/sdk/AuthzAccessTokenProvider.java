package com.platform.authz.sdk;

import java.util.Optional;

/**
 * Provides the JWT access token used by AuthZ runtime endpoints.
 *
 * <p>{@code GET /v1/users/{userId}/permissions} and {@code POST /v1/authz/check}
 * require user JWT authentication in the current AuthZ service implementation.
 * Consumers may override the default bean when the token source is not the current
 * HTTP request.
 */
@FunctionalInterface
public interface AuthzAccessTokenProvider {

    /**
     * Resolves the current bearer access token without the {@code Bearer } prefix.
     *
     * @return the current access token, when available
     */
    Optional<String> resolveAccessToken();
}
