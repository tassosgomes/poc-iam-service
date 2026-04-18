package com.platform.authz.sdk;

import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Default access-token provider that propagates the bearer token from the current HTTP request.
 */
public class RequestContextAccessTokenProvider implements AuthzAccessTokenProvider {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Optional<String> resolveAccessToken() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return Optional.empty();
        }

        String authorizationHeader = servletRequestAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }

        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String accessToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(accessToken);
    }
}
