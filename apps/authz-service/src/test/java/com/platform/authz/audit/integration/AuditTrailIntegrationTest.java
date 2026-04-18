package com.platform.authz.audit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.catalog.infra.SpringDataPermissionRepository;
import com.platform.authz.iam.application.UserSearchPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
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
class AuditTrailIntegrationTest {

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
    private SpringDataPermissionRepository permissionRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private UserSearchPort userSearchPort;

    @BeforeEach
    void setUp() {
        when(userSearchPort.userExists(anyString())).thenReturn(true);
    }

    @Test
    void getAuditEvents_WithPlatformAdmin_ShouldReturnFilteredPaginatedResults() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("Audit-" + suffix, "audit-" + suffix);
        awaitAuditEventCount("MODULE_CREATED", moduleInfo.moduleId(), 1);

        // Act & Assert
        mockMvc.perform(get("/v1/audit/events")
                        .with(platformAdminJwt())
                        .param("eventType", "MODULE_CREATED")
                        .param("moduleId", moduleInfo.moduleId())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventType").value("MODULE_CREATED"))
                .andExpect(jsonPath("$.data[0].payload.moduleId").value(moduleInfo.moduleId()))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.size").value(10))
                .andExpect(jsonPath("$.pagination.total").value(1));
    }

    @Test
    void getAuditEvents_WithAuditorRole_ShouldAllowAccess() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("AuditRead-" + suffix, "audit-read-" + suffix);
        awaitAuditEventCount("MODULE_CREATED", moduleInfo.moduleId(), 1);

        // Act & Assert
        mockMvc.perform(get("/v1/audit/events")
                        .with(auditorJwt())
                        .param("moduleId", moduleInfo.moduleId())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void getAuditEvents_WithoutAuditRole_ShouldReturnForbidden() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("Forbidden-" + suffix, "forbidden-" + suffix);
        awaitAuditEventCount("MODULE_CREATED", moduleInfo.moduleId(), 1);

        // Act & Assert
        mockMvc.perform(get("/v1/audit/events")
                        .with(nonAuditorJwt())
                        .param("moduleId", moduleInfo.moduleId())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignRoleAndInvalidModuleKey_ShouldRecordAuditEventsAndFilterByEventType() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        syncPermissions(moduleInfo, """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(moduleInfo.allowedPrefix()));
        String roleId = createRole(moduleInfo);

        // Act - assign role and trigger invalid module key
        mockMvc.perform(post("/v1/users/{userId}/roles", "user-target")
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleId": "%s"
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        awaitAuditEventCount("ROLE_ASSIGNED", moduleInfo.moduleId(), 1);

        mockMvc.perform(post("/v1/catalog/sync")
                        .header("Authorization", "Bearer invalid-secret")
                        .header("X-Module-Id", moduleInfo.moduleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "schemaVersion": "1.0",
                                  "payloadHash": "invalid-%s",
                                  "permissions": []
                                }
                                """.formatted(moduleInfo.moduleId(), suffix)))
                .andExpect(status().isUnauthorized());
        awaitAuditEventCount("KEY_AUTH_FAILED", moduleInfo.moduleId(), 1);

        // Assert - query filtered events
        mockMvc.perform(get("/v1/audit/events")
                        .with(platformAdminJwt())
                        .param("eventType", "ROLE_ASSIGNED")
                        .param("moduleId", moduleInfo.moduleId())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].target").value("user-target"))
                .andExpect(jsonPath("$.data[0].payload.roleName").value(rolePrefix(moduleInfo.allowedPrefix()) + "_GERENTE"));

        mockMvc.perform(get("/v1/audit/events")
                        .with(platformAdminJwt())
                        .param("eventType", "KEY_AUTH_FAILED")
                        .param("moduleId", moduleInfo.moduleId())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].payload.reason").value("not_found"));
    }

    private String createRole(ModuleInfo moduleInfo) throws Exception {
        String permissionId = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(moduleInfo.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).getFirst().getId().toString();

        String response = mockMvc.perform(post("/v1/roles")
                        .with(platformAdminJwt())
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
        String response = mockMvc.perform(post("/v1/modules")
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

    private void awaitAuditEventCount(String eventType, String moduleId, int expectedCount) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        Integer actualCount = null;

        while (Instant.now().isBefore(deadline)) {
            actualCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_event WHERE event_type = ? AND payload ->> 'moduleId' = ?",
                    Integer.class,
                    eventType,
                    moduleId
            );
            if (actualCount != null && actualCount == expectedCount) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
        }

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor platformAdminJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("admin-user")
                .claim("roles", List.of("PLATFORM_ADMIN")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auditorJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("audit-user")
                .claim("roles", List.of("AUDITOR")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor nonAuditorJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("viewer-user")
                .claim("roles", List.of("VENDAS_USER_MANAGER")));
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
