package com.platform.authz.shared.security;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ModuleScopeExtractor {

    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_PLATFORM_ADMIN";
    private static final String USER_MANAGER_SUFFIX = "_USER_MANAGER";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String MODULE_PREFIX = "MODULE_";

    public boolean isPlatformAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Set<String> authorities = extractAuthorities(authentication);
        return authorities.contains(PLATFORM_ADMIN_AUTHORITY)
                || extractNormalizedRoles(authentication).contains("PLATFORM_ADMIN");
    }

    public List<String> extractManageableModules(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        Set<String> modules = new LinkedHashSet<>();
        extractAuthorities(authentication).stream()
                .filter(authority -> authority.startsWith(ROLE_PREFIX) && authority.endsWith(USER_MANAGER_SUFFIX))
                .map(authority -> authority.substring(
                        ROLE_PREFIX.length(),
                        authority.length() - USER_MANAGER_SUFFIX.length()
                ).toLowerCase())
                .forEach(modules::add);
        extractNormalizedRoles(authentication).stream()
                .filter(role -> role.endsWith(USER_MANAGER_SUFFIX))
                .map(role -> role.substring(0, role.length() - USER_MANAGER_SUFFIX.length()).toLowerCase())
                .forEach(modules::add);

        return List.copyOf(modules);
    }

    private Set<String> extractAuthorities(Authentication authentication) {
        Set<String> authorities = new LinkedHashSet<>();
        authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(authorities::add);
        return authorities;
    }

    private Set<String> extractNormalizedRoles(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Set.of();
        }

        return JwtAuthorizationConverter.extractNormalizedRoles(jwt);
    }
}
