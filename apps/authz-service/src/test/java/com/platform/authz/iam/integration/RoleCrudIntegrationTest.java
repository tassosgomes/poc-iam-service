package com.platform.authz.iam.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.platform.authz.catalog.domain.PermissionStatus;
import com.platform.authz.catalog.infra.SpringDataPermissionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
class RoleCrudIntegrationTest {

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

    @Test
    void roleCrud_WithScopedManager_ShouldCreateReadUpdateCloneListAndDeleteRole() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String allowedPrefix = "vendas-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey("Sales-" + suffix, allowedPrefix);
        syncPermissions(
                moduleInfo,
                """
                [
                  {"code": "%s.orders.read", "description": "Read orders"},
                  {"code": "%s.orders.update", "description": "Update orders"}
                ]
                """.formatted(allowedPrefix, allowedPrefix)
        );

        List<String> permissionIds = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(moduleInfo.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).stream()
                .map(permission -> permission.getId().toString())
                .sorted()
                .toList();

        // Act & Assert — create
        String createResponse = mockMvc.perform(post(ROLES_ENDPOINT)
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "name": "%s_GERENTE",
                                  "description": "Sales manager",
                                  "permissionIds": ["%s", "%s"]
                                }
                                """.formatted(
                                moduleInfo.moduleId(),
                                rolePrefix(moduleInfo.allowedPrefix()),
                                permissionIds.get(0),
                                permissionIds.get(1)
                        )))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/v1/roles/[a-f0-9\\-]+")))
                .andExpect(jsonPath("$.name").value(rolePrefix(moduleInfo.allowedPrefix()) + "_GERENTE"))
                .andExpect(jsonPath("$.permissions[0].description").value("Read orders"))
                .andExpect(jsonPath("$.permissions[1].description").value("Update orders"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String roleId = JsonPath.read(createResponse, "$.roleId");

        // Assert — get
        mockMvc.perform(get(ROLES_ENDPOINT + "/{id}", roleId)
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId))
                .andExpect(jsonPath("$.permissions.length()").value(2));

        // Act & Assert — update
        mockMvc.perform(put(ROLES_ENDPOINT + "/{id}", roleId)
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s_COORDENADOR",
                                  "description": "Sales coordinator",
                                  "permissionIds": ["%s"]
                                }
                                """.formatted(rolePrefix(moduleInfo.allowedPrefix()), permissionIds.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(rolePrefix(moduleInfo.allowedPrefix()) + "_COORDENADOR"))
                .andExpect(jsonPath("$.permissions.length()").value(1));

        // Act & Assert — clone
        String cloneResponse = mockMvc.perform(post(ROLES_ENDPOINT + "/{id}/clone", roleId)
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(rolePrefix(moduleInfo.allowedPrefix()) + "_COORDENADOR_COPY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String clonedRoleId = JsonPath.read(cloneResponse, "$.roleId");

        // Assert — list
        mockMvc.perform(get(ROLES_ENDPOINT)
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix()))
                        .param("moduleId", moduleInfo.moduleId())
                        .param("_page", "1")
                        .param("_size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.total").value(2));

        // Act & Assert — delete cloned role
        mockMvc.perform(delete(ROLES_ENDPOINT + "/{id}", clonedRoleId)
                        .with(moduleManagerJwt(moduleInfo.allowedPrefix())))
                .andExpect(status().isNoContent());
    }

    @Test
    void createRole_WithInvalidName_ShouldReturn422() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String allowedPrefix = "vendas-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey("Sales-" + suffix, allowedPrefix);
        syncPermissions(
                moduleInfo,
                """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(allowedPrefix)
        );
        String permissionId = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(moduleInfo.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).getFirst().getId().toString();

        // Act & Assert
        mockMvc.perform(post(ROLES_ENDPOINT)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "name": "GERENTE_VENDAS",
                                  "description": "Sales manager",
                                  "permissionIds": ["%s"]
                                }
                                """.formatted(moduleInfo.moduleId(), permissionId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://authz.platform/errors/invalid-role-name"));
    }

    @Test
    void createRole_WithPermissionFromAnotherModule_ShouldReturn422WithMismatchCode() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo salesModule = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        ModuleInfo stockModule = createModuleAndGetKey("Stock-" + suffix, "estoque-" + suffix);

        syncPermissions(salesModule, """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(salesModule.allowedPrefix()));
        syncPermissions(stockModule, """
                [
                  {"code": "%s.items.read", "description": "Read items"}
                ]
                """.formatted(stockModule.allowedPrefix()));

        String foreignPermissionId = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(stockModule.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).getFirst().getId().toString();

        // Act & Assert
        mockMvc.perform(post(ROLES_ENDPOINT)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "name": "%s_GERENTE",
                                  "description": "Sales manager",
                                  "permissionIds": ["%s"]
                                }
                                """.formatted(
                                salesModule.moduleId(),
                                rolePrefix(salesModule.allowedPrefix()),
                                foreignPermissionId
                        )))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("permission_module_mismatch"));
    }

    @Test
    void deleteRole_WithActiveUserRole_ShouldReturn409() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo moduleInfo = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        syncPermissions(
                moduleInfo,
                """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(moduleInfo.allowedPrefix())
        );
        String permissionId = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(moduleInfo.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).getFirst().getId().toString();

        String roleResponse = mockMvc.perform(post(ROLES_ENDPOINT)
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

        String roleId = JsonPath.read(roleResponse, "$.roleId");
        jdbcTemplate.update(
                """
                INSERT INTO user_role (id, user_id, role_id, assigned_by, assigned_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                "user-demo",
                UUID.fromString(roleId),
                "admin-user",
                Instant.now()
        );

        // Act & Assert
        mockMvc.perform(delete(ROLES_ENDPOINT + "/{id}", roleId)
                        .with(platformAdminJwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void createRole_WithManagerFromAnotherModule_ShouldReturn403() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        ModuleInfo salesModule = createModuleAndGetKey("Sales-" + suffix, "vendas-" + suffix);
        syncPermissions(salesModule, """
                [
                  {"code": "%s.orders.read", "description": "Read orders"}
                ]
                """.formatted(salesModule.allowedPrefix()));
        String permissionId = permissionRepository.findByModuleIdAndStatusIn(
                        UUID.fromString(salesModule.moduleId()),
                        List.of(PermissionStatus.ACTIVE, PermissionStatus.DEPRECATED)
                ).getFirst().getId().toString();

        // Act & Assert
        mockMvc.perform(post(ROLES_ENDPOINT)
                        .with(moduleManagerJwt("estoque"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "%s",
                                  "name": "%s_GERENTE",
                                  "description": "Sales manager",
                                  "permissionIds": ["%s"]
                                }
                                """.formatted(salesModule.moduleId(), rolePrefix(salesModule.allowedPrefix()), permissionId)))
                .andExpect(status().isForbidden());
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
