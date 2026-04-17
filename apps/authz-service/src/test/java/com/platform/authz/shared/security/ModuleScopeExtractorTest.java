package com.platform.authz.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ModuleScopeExtractorTest {

    private final ModuleScopeExtractor extractor = new ModuleScopeExtractor();

    @Test
    void isPlatformAdmin_WithRolePlatformAdminAuthority_ShouldReturnTrue() {
        // Arrange
        Authentication auth = buildAuthentication(
                "admin-user",
                List.of("platform"),
                List.of("PLATFORM_ADMIN")
        );

        // Act & Assert
        assertThat(extractor.isPlatformAdmin(auth)).isTrue();
    }

    @Test
    void isPlatformAdmin_WithPlatformAdminInJwtClaims_ShouldReturnTrue() {
        // Arrange
        Jwt jwt = buildJwt("admin-user", List.of("platform"), List.of("PLATFORM_ADMIN"));
        Authentication auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("MODULE_platform")));

        // Act & Assert
        assertThat(extractor.isPlatformAdmin(auth)).isTrue();
    }

    @Test
    void isPlatformAdmin_WithScopedManager_ShouldReturnFalse() {
        // Arrange
        Authentication auth = buildAuthentication(
                "vendas-mgr",
                List.of("vendas"),
                List.of("VENDAS_USER_MANAGER")
        );

        // Act & Assert
        assertThat(extractor.isPlatformAdmin(auth)).isFalse();
    }

    @Test
    void isPlatformAdmin_WithNullAuthentication_ShouldReturnFalse() {
        // Act & Assert
        assertThat(extractor.isPlatformAdmin(null)).isFalse();
    }

    @Test
    void extractManageableModules_WithSingleManagerRole_ShouldReturnModule() {
        // Arrange
        Authentication auth = buildAuthentication(
                "vendas-mgr",
                List.of("vendas"),
                List.of("VENDAS_USER_MANAGER")
        );

        // Act
        List<String> modules = extractor.extractManageableModules(auth);

        // Assert
        assertThat(modules).containsExactly("vendas");
    }

    @Test
    void extractManageableModules_WithMultipleManagerRoles_ShouldReturnAllModules() {
        // Arrange
        Authentication auth = buildAuthentication(
                "multi-mgr",
                List.of("vendas", "estoque"),
                List.of("VENDAS_USER_MANAGER", "ESTOQUE_USER_MANAGER")
        );

        // Act
        List<String> modules = extractor.extractManageableModules(auth);

        // Assert
        assertThat(modules).containsExactlyInAnyOrder("vendas", "estoque");
    }

    @Test
    void extractManageableModules_WithPlatformAdmin_ShouldReturnEmptyList() {
        // Arrange
        Authentication auth = buildAuthentication(
                "admin-user",
                List.of("platform"),
                List.of("PLATFORM_ADMIN")
        );

        // Act
        List<String> modules = extractor.extractManageableModules(auth);

        // Assert
        assertThat(modules).isEmpty();
    }

    @Test
    void extractManageableModules_WithOperatorRole_ShouldReturnEmptyList() {
        // Arrange
        Authentication auth = buildAuthentication(
                "vendas-op",
                List.of("vendas"),
                List.of("VENDAS_OPERATOR")
        );

        // Act
        List<String> modules = extractor.extractManageableModules(auth);

        // Assert
        assertThat(modules).isEmpty();
    }

    @Test
    void extractManageableModules_WithNullAuthentication_ShouldReturnEmptyList() {
        // Act & Assert
        assertThat(extractor.extractManageableModules(null)).isEmpty();
    }

    @Test
    void extractManageableModules_WithLowercaseRolesInClaims_ShouldNormalizeAndExtract() {
        // Arrange
        Jwt jwt = buildJwt("mgr", List.of("vendas"), List.of("vendas_user_manager"));
        JwtAuthorizationConverter converter = new JwtAuthorizationConverter();
        Authentication auth = converter.convert(jwt);

        // Act
        List<String> modules = extractor.extractManageableModules(auth);

        // Assert
        assertThat(modules).containsExactly("vendas");
    }

    @Test
    void extractManageableModules_WithMixedRoles_ShouldReturnOnlyManagerModules() {
        // Arrange
        Authentication auth = buildAuthentication(
                "mixed-user",
                List.of("vendas", "estoque"),
                List.of("VENDAS_USER_MANAGER", "ESTOQUE_OPERATOR", "AUDITOR")
        );

        // Act
        List<String> modules = extractor.extractManageableModules(auth);

        // Assert
        assertThat(modules).containsExactly("vendas");
    }

    @Test
    void canManageModule_WithHyphenatedModulePrefix_ShouldNormalizeAndMatch() {
        // Arrange
        Authentication auth = buildAuthentication(
                "sales-mgr",
                List.of("vendas-2026"),
                List.of("VENDAS_2026_USER_MANAGER")
        );

        // Act & Assert
        assertThat(extractor.canManageModule(auth, "vendas-2026")).isTrue();
    }

    @Test
    void canManageModule_WithUnauthorizedModule_ShouldReturnFalse() {
        // Arrange
        Authentication auth = buildAuthentication(
                "sales-mgr",
                List.of("vendas"),
                List.of("VENDAS_USER_MANAGER")
        );

        // Act & Assert
        assertThat(extractor.canManageModule(auth, "estoque")).isFalse();
    }

    private Authentication buildAuthentication(String subject, List<String> modules, List<String> roles) {
        Jwt jwt = buildJwt(subject, modules, roles);
        JwtAuthorizationConverter converter = new JwtAuthorizationConverter();
        return converter.convert(jwt);
    }

    private Jwt buildJwt(String subject, List<String> modules, List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (modules != null) {
            builder.claim("module_membership", modules);
        }

        if (roles != null) {
            builder.claim("roles", roles);
        }

        return builder.build();
    }
}
