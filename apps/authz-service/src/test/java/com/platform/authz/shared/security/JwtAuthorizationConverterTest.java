package com.platform.authz.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtAuthorizationConverterTest {

    private final JwtAuthorizationConverter converter = new JwtAuthorizationConverter();

    @Test
    void convert_WithModuleMembershipAndRoles_ShouldMapBothToAuthorities() {
        // Arrange
        Jwt jwt = buildJwt(
                "user-vendas-mgr",
                List.of("vendas"),
                List.of("VENDAS_USER_MANAGER")
        );

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(token.getName()).isEqualTo("user-vendas-mgr");

        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactlyInAnyOrder(
                "MODULE_vendas",
                "ROLE_VENDAS_USER_MANAGER"
        );
    }

    @Test
    void convert_WithPlatformAdmin_ShouldMapPlatformAdminRoleAndModuleAuthority() {
        // Arrange
        Jwt jwt = buildJwt(
                "user-admin",
                List.of("platform"),
                List.of("PLATFORM_ADMIN")
        );

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactlyInAnyOrder(
                "MODULE_platform",
                "ROLE_PLATFORM_ADMIN"
        );
    }

    @Test
    void convert_WithLowercasePlatformRolesClaim_ShouldNormalizeAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("module_membership", List.of("platform"))
                .claim("platform_roles", List.of("platform_admin", "auditor"))
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactlyInAnyOrder(
                "MODULE_platform",
                "ROLE_PLATFORM_ADMIN",
                "ROLE_AUDITOR"
        );
    }

    @Test
    void convert_WithMultipleModulesAndRoles_ShouldMapAll() {
        // Arrange
        Jwt jwt = buildJwt(
                "user-multi",
                List.of("vendas", "estoque"),
                List.of("VENDAS_USER_MANAGER", "ESTOQUE_USER_MANAGER")
        );

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactlyInAnyOrder(
                "MODULE_vendas",
                "MODULE_estoque",
                "ROLE_VENDAS_USER_MANAGER",
                "ROLE_ESTOQUE_USER_MANAGER"
        );
    }

    @Test
    void convert_WithNoModuleMembership_ShouldMapOnlyRoles() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-no-modules")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of("AUDITOR"))
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactly("ROLE_AUDITOR");
    }

    @Test
    void convert_WithNoRoles_ShouldMapOnlyModules() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-no-roles")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("module_membership", List.of("vendas"))
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        List<String> authorities = token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorities).containsExactly("MODULE_vendas");
    }

    @Test
    void convert_WithNoClaims_ShouldReturnEmptyAuthorities() {
        // Arrange
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("user-empty")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("iss", "https://issuer.test")
                .build();

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getAuthorities()).isEmpty();
        assertThat(token.getName()).isEqualTo("user-empty");
    }

    @Test
    void extractAuthorities_ShouldPreserveInsertionOrder() {
        // Arrange
        Jwt jwt = buildJwt(
                "user-order",
                List.of("alpha", "beta"),
                List.of("ALPHA_MANAGER", "BETA_OPERATOR")
        );

        // Act
        Collection<GrantedAuthority> authorities = converter.extractAuthorities(jwt);

        // Assert
        List<String> authorityStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(authorityStrings).containsExactly(
                "MODULE_alpha",
                "MODULE_beta",
                "ROLE_ALPHA_MANAGER",
                "ROLE_BETA_OPERATOR"
        );
    }

    @Test
    void convert_ShouldPreserveJwtAsPrincipal() {
        // Arrange
        Jwt jwt = buildJwt("user-test", List.of("vendas"), List.of("VENDAS_USER_MANAGER"));

        // Act
        AbstractAuthenticationToken token = converter.convert(jwt);

        // Assert
        assertThat(token.getPrincipal()).isSameAs(jwt);
        assertThat(token.getCredentials()).isInstanceOf(Jwt.class);
    }

    private Jwt buildJwt(String subject, List<String> modules, List<String> roles) {
        return buildJwt(subject, modules, Map.of("roles", roles));
    }

    private Jwt buildJwt(String subject, List<String> modules, Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (modules != null) {
            builder.claim("module_membership", modules);
        }

        claims.forEach(builder::claim);
        return builder.build();
    }
}
