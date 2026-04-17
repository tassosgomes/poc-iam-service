package com.platform.authz.modules.api;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminAccessEvaluator {
    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    public void assertPlatformAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !hasPlatformAdminRole(authentication)) {
            throw new PlatformAdminRequiredException();
        }
    }

    private boolean hasPlatformAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(this::isPlatformAdminAuthority)
                || extractJwtRoles(authentication).stream().anyMatch(PLATFORM_ADMIN_ROLE::equals);
    }

    private boolean isPlatformAdminAuthority(String authority) {
        return Objects.equals(PLATFORM_ADMIN_ROLE, authority)
                || Objects.equals("ROLE_" + PLATFORM_ADMIN_ROLE, authority);
    }

    private Collection<String> extractJwtRoles(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return List.of();
        }

        return jwt.getClaimAsStringList("roles") != null
                ? jwt.getClaimAsStringList("roles")
                : List.of();
    }
}
