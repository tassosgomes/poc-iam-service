package com.platform.authz.iam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.authz.iam.application.UserSearchPort;
import io.micrometer.core.instrument.Gauge;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PermissionQueryControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authz")
            .withUsername("authz")
            .withPassword("authz");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTHZ_DB_URL", postgres::getJdbcUrl);
        registry.add("AUTHZ_DB_USER", postgres::getUsername);
        registry.add("AUTHZ_DB_PASS", postgres::getPassword);
        registry.add("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI", () -> "https://issuer.example.test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserSearchPort userSearchPort;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_role");
        jdbcTemplate.update("DELETE FROM role_permission");
        jdbcTemplate.update("DELETE FROM role");
        jdbcTemplate.update("DELETE FROM permission");
        jdbcTemplate.update("DELETE FROM module_key");
        jdbcTemplate.update("DELETE FROM module");
        when(userSearchPort.userExists(anyString())).thenReturn(true);
    }

    @Test
    void getUserPermissions_WithSameAuthenticatedUser_ShouldReturnOk() throws Exception {
        SeededRole seededRole = seedRoleWithPermission("user-self", "vendas.orders.read", "VENDAS_VIEWER");

        mockMvc.perform(get("/v1/users/{userId}/permissions", "user-self")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-self")
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-self"))
                .andExpect(jsonPath("$.permissions[0]").value("vendas.orders.read"))
                .andExpect(jsonPath("$.ttlSeconds").value(600L));

        assertThat(seededRole.roleId()).isNotNull();
    }

    @Test
    void getUserPermissions_WithAnotherUserWithoutPlatformAdmin_ShouldReturnForbidden() throws Exception {
        seedRoleWithPermission("user-target", "vendas.orders.read", "VENDAS_VIEWER");

        mockMvc.perform(get("/v1/users/{userId}/permissions", "user-target")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("other-user")
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void getUserPermissions_WithRevokedRolesAndRemovedPermissions_ShouldReturnOnlyActiveAndDeprecatedFromActiveAssignments()
            throws Exception {
        seedRoleAssignment("user-filter", "vendas.orders.read", "ACTIVE", "VENDAS_VIEWER", null);
        seedRoleAssignment("user-filter", "vendas.orders.export", "DEPRECATED", "VENDAS_EXPORTER", null);
        seedRoleAssignment("user-filter", "vendas.orders.remove", "REMOVED", "VENDAS_REMOVED", null);
        seedRoleAssignment(
                "user-filter",
                "vendas.orders.approve",
                "ACTIVE",
                "VENDAS_REVOKED",
                Instant.parse("2026-04-20T12:05:00Z")
        );

        mockMvc.perform(get("/v1/users/{userId}/permissions", "user-filter")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-filter")
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.permissions[0]").value("vendas.orders.export"))
                .andExpect(jsonPath("$.permissions[1]").value("vendas.orders.read"));
    }

    @Test
    void getUserPermissions_AfterRoleAssignment_ShouldReturnUpdatedPermissionsAndCacheHitRatio() throws Exception {
        SeededRole initialRole = seedRoleWithPermission("user-target", "vendas.orders.read", "VENDAS_VIEWER");
        SeededRole newRole = seedRoleWithPermission(null, "vendas.orders.update", "VENDAS_EDITOR");

        mockMvc.perform(get("/v1/users/{userId}/permissions", "user-target")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-target")
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(1))
                .andExpect(jsonPath("$.permissions[0]").value("vendas.orders.read"));

        mockMvc.perform(get("/v1/users/{userId}/permissions", "user-target")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-target")
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(1));

        Gauge cacheHitRatioGauge = meterRegistry.find("authz_user_permissions_cache_hit_ratio")
                .tag("cache", "userPermissions")
                .gauge();
        assertThat(cacheHitRatioGauge).isNotNull();
        assertThat(cacheHitRatioGauge.value()).isGreaterThan(0.0d);

        mockMvc.perform(post("/v1/users/{userId}/roles", "user-target")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("admin-user")
                                .claim("roles", List.of("PLATFORM_ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(newRole.roleId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/users/{userId}/permissions", "user-target")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-target")
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2))
                .andExpect(jsonPath("$.permissions[0]").value("vendas.orders.read"))
                .andExpect(jsonPath("$.permissions[1]").value("vendas.orders.update"));

        mockMvc.perform(post("/v1/users/{userId}/roles", "user-target")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("admin-user")
                                .claim("roles", List.of("PLATFORM_ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(initialRole.roleId())))
                .andExpect(status().isOk());
    }

    private SeededRole seedRoleWithPermission(String userId, String permissionCode, String roleName) {
        return seedRoleAssignment(userId, permissionCode, "ACTIVE", roleName, null);
    }

    private SeededRole seedRoleAssignment(
            String userId,
            String permissionCode,
            String permissionStatus,
            String roleName,
            Instant revokedAt
    ) {
        UUID moduleId = ensureSalesModule();
        UUID permissionId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.parse("2026-04-20T12:00:00Z");

        jdbcTemplate.update(
                """
                        INSERT INTO permission (
                            id, module_id, code, description, status, sunset_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                permissionId,
                moduleId,
                permissionCode,
                permissionCode + " description",
                permissionStatus,
                null,
                Timestamp.from(now),
                Timestamp.from(now)
        );
        jdbcTemplate.update(
                """
                        INSERT INTO role (id, module_id, name, description, created_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                roleId,
                moduleId,
                roleName,
                roleName + " description",
                "seed",
                Timestamp.from(now)
        );
        jdbcTemplate.update("INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);

        if (userId != null) {
            jdbcTemplate.update(
                    """
                            INSERT INTO user_role (
                                id, user_id, role_id, assigned_by, assigned_at, revoked_at, revoked_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    UUID.randomUUID(),
                    userId,
                    roleId,
                    "seed",
                    Timestamp.from(now),
                    revokedAt == null ? null : Timestamp.from(revokedAt),
                    revokedAt == null ? null : "seed-revoker"
            );
        }

        return new SeededRole(roleId, permissionCode);
    }

    private UUID ensureSalesModule() {
        List<UUID> moduleIds = jdbcTemplate.query(
                "SELECT id FROM module WHERE allowed_prefix = 'vendas'",
                (resultSet, rowNum) -> UUID.fromString(resultSet.getString("id"))
        );
        if (!moduleIds.isEmpty()) {
            return moduleIds.getFirst();
        }

        UUID moduleId = UUID.randomUUID();
        Instant now = Instant.parse("2026-04-20T12:00:00Z");
        jdbcTemplate.update(
                """
                        INSERT INTO module (id, name, allowed_prefix, description, created_by, created_at, last_heartbeat_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                moduleId,
                "Sales",
                "vendas",
                "Sales module",
                "seed",
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return moduleId;
    }

    private record SeededRole(UUID roleId, String permissionCode) {
    }
}
