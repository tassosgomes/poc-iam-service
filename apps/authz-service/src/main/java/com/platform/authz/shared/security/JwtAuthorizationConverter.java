package com.platform.authz.shared.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthorizationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String MODULE_MEMBERSHIP_CLAIM = "module_membership";
    static final String PLATFORM_ROLES_CLAIM = "platform_roles";
    static final String ROLES_CLAIM = "roles";
    static final String MODULE_AUTHORITY_PREFIX = "MODULE_";
    static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
    private static final Pattern INVALID_ROLE_CHARACTER_PATTERN = Pattern.compile("[^A-Z0-9]+");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        extractModules(jwt).forEach(module -> authorities.add(new SimpleGrantedAuthority(MODULE_AUTHORITY_PREFIX + module)));
        extractNormalizedRoles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role)));

        return authorities;
    }

    static Set<String> extractModules(Jwt jwt) {
        Set<String> modules = new LinkedHashSet<>();
        List<String> moduleMembership = jwt.getClaimAsStringList(MODULE_MEMBERSHIP_CLAIM);
        if (moduleMembership == null) {
            return modules;
        }

        for (String module : moduleMembership) {
            if (module != null && !module.isBlank()) {
                modules.add(module.trim().toLowerCase());
            }
        }
        return modules;
    }

    static Set<String> extractNormalizedRoles(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();
        extractNormalizedRoles(jwt.getClaimAsStringList(PLATFORM_ROLES_CLAIM), roles);
        extractNormalizedRoles(jwt.getClaimAsStringList(ROLES_CLAIM), roles);
        return roles;
    }

    private static void extractNormalizedRoles(List<String> rawRoles, Set<String> target) {
        if (rawRoles == null) {
            return;
        }

        for (String rawRole : rawRoles) {
            String normalizedRole = normalizeRole(rawRole);
            if (normalizedRole != null) {
                target.add(normalizedRole);
            }
        }
    }

    static String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }

        String normalizedRole = INVALID_ROLE_CHARACTER_PATTERN.matcher(rawRole.trim().toUpperCase()).replaceAll("_");
        normalizedRole = normalizedRole.replaceAll("^_+|_+$", "");
        normalizedRole = normalizedRole.replaceAll("_+", "_");

        return normalizedRole.isBlank() ? null : normalizedRole;
    }
}
