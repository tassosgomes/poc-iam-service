package com.platform.authz.iam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.catalog.infra.SpringDataPermissionRepository;
import com.platform.authz.iam.application.UserSearchPort;
import java.util.List;
import java.util.Locale;
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
class UserRoleAssignmentIntegrationTest {

    private static final String MODULES_ENDPOINT = "/v1/modules";
    private static final String ROLES_ENDPOINT = "/v1/roles";

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
    private SpringDataPermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserSearchPort userSearchPort;

    @BeforeEach
    void setUp() {
        when(userSearchPort.userExists(anyString())).thenReturn(true);
    }

    @Test
    void assignRole_WithPlatformAdmin_ShouldCreateIdempotentlyListAndRevoke() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        syncPermissions(moduleInfo, """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(moduleInfo.allowedPrefix()));
        String roleId = createRole(moduleInfo, platformAdminJwt());

        // Act & Assert - create
        mockMvc.perform(post("/v1/users/{userId}/roles", "user-target")
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleId").value(roleId));

        // Act & Assert - idempotent reassign
        mockMvc.perform(post("/v1/users/{userId}/roles", "user-target")
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId));

        // Assert - list active roles
        mockMvc.perform(get("/v1/users/{userId}/roles", "user-target")
                        .with(platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roleId").value(roleId));

        // Act & Assert - revoke
        mockMvc.perform(delete("/v1/users/{userId}/roles/{roleId}", "user-target", roleId)
                        .with(platformAdminJwt()))
                .andExpect(status().isNoContent());

        // Assert - revocation persisted and audit events recorded
        String revokedBy = jdbcTemplate.queryForObject(
                "SELECT revoked_by FROM user_role WHERE user_id = ? AND role_id = ?",
                String.class,
                "user-target",
                UUID.fromString(roleId)
        );
        Integer assignedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE event_type = 'ROLE_ASSIGNED' AND target = ?",
                Integer.class,
                "user-target"
        );
        Integer revokedEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE event_type = 'ROLE_REVOKED' AND target = ?",
                Integer.class,
                "user-target"
        );

        assertThat(revokedBy).isEqualTo("admin-user");
        assertThat(assignedEvents).isEqualTo(1);
        assertThat(revokedEvents).isEqualTo(1);
    }

    @Test
    void assignRole_WithScopedManagerFromSameModule_ShouldReturnCreated() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        syncPermissions(moduleInfo, """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(moduleInfo.allowedPrefix()));
        String roleId = createRole(moduleInfo, platformAdminJwt());

        // Act & Assert
        mockMvc.perform(post("/v1/users/{userId}/roles", "user-sales")
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleId").value(roleId));
    }

    @Test
    void assignRole_WithScopedManagerFromAnotherModule_ShouldReturnAdminScopeViolation() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo salesModule = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        ModuleInfo stockModule = createModuleAndGetKey("Stock-" + suffix, "estoque-" + suffix);
        syncPermissions(salesModule, """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(salesModule.allowedPrefix()));
        String roleId = createRole(salesModule, platformAdminJwt());

        // Act & Assert
        mockMvc.perform(post("/v1/users/{userId}/roles", "user-target")
                        .with(moduleManagerJwt(stockModule.allowedPrefix()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://authz.platform/errors/admin-scope-violation"))
                .andExpect(jsonPath("$.errorCode").value("admin_scope_violation"));
    }

    private String createRole(
            ModuleInfo moduleInfo,
            org.springframework.test.web.servlet.request.RequestPostProcessor requestPostProcessor
    ) throws Exception {
        String permissionId = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(moduleInfo.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).getFirst().getId().toString();

        String response = mockMvc.perform(post(ROLES_ENDPOINT)
                        .with(requestPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "name": "%s_GERENTE",
                                  "description": "Sales manager",
                                  "permissionIds": ["%s"]
                                }
                                """.formatted(moduleInfo.moduleId(), rolePrefix(moduleInfo.allowedPrefix()), permissionId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.roleId");
    }

    private ModuleInfo createModuleAndGetKey(String name, String allowedPrefix) throws Exception {
        String response = mockMvc.perform(post(MODULES_ENDPOINT)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "allowedPrefix": "%s",
                                  "description": "%s module"
                                }
                                """.formatted(name, allowedPrefix, name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new ModuleInfo(
                JsonPath.read(response, "$.moduleId"),
                JsonPath.read(response, "$.secret"),
                allowedPrefix
        );
    }

    private void syncPermissions(ModuleInfo moduleInfo, String permissionsJson) throws Exception {
        mockMvc.perform(post("/v1/catalog/sync")
                        .header("Authorization", "Bearer " + moduleInfo.secret())
                        .header("X-Module-Id", moduleInfo.moduleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "schemaVersion": "1.0",
                                  "payloadHash": "hash-%s",
                                  "permissions": %s
                                }
                                """.formatted(moduleInfo.moduleId(), uniqueSuffix(), permissionsJson)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor platformAdminJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("admin-user")
                .claim("roles", List.of("PLATFORM_ADMIN")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor moduleManagerJwt(String allowedPrefix) {
        return jwt().jwt(jwt -> jwt
                .subject("module-manager")
                .claim("roles", List.of(rolePrefix(allowedPrefix) + "_USER_MANAGER")));
    }

    private static String rolePrefix(String allowedPrefix) {
        return allowedPrefix.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record ModuleInfo(String moduleId, String secret, String allowedPrefix) {
    }
}
