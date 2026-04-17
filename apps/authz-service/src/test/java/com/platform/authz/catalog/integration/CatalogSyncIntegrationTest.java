package com.platform.authz.catalog.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.platform.authz.catalog.infra.SpringDataPermissionRepository;
import com.platform.authz.catalog.infra.SpringDataSyncEventRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class CatalogSyncIntegrationTest {

    private static final String SYNC_ENDPOINT = "/v1/catalog/sync";
    private static final String MODULES_ENDPOINT = "/v1/modules";

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
    private SpringDataSyncEventRepository syncEventRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void sync_WithValidPayload_ShouldReturnAddedPermissions() throws Exception {
        // Arrange — create module and get its key
        String suffix = uniqueSuffix();
        String moduleName = "vendas-" + suffix;
        String allowedPrefix = "vendas-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);

        String syncPayload = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-%s",
                  "permissions": [
                    {"code": "%s.orders.create", "description": "Create orders"},
                    {"code": "%s.orders.read", "description": "Read orders"}
                  ]
                }
                """.formatted(moduleInfo.moduleId, suffix, allowedPrefix, allowedPrefix);

        // Act & Assert — first sync creates permissions
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.added").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.deprecated").value(0))
                .andExpect(jsonPath("$.catalogVersion").isNotEmpty());
    }

    @Test
    void sync_WithIdenticalPayload_ShouldReturnChangedFalse() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleName = "estoque-" + suffix;
        String allowedPrefix = "estoque-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);

        String payloadHash = "idempotent-hash-" + suffix;
        String syncPayload = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "%s",
                  "permissions": [
                    {"code": "%s.items.read", "description": "Read items"}
                  ]
                }
                """.formatted(moduleInfo.moduleId, payloadHash, allowedPrefix);

        // First sync
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));

        // Act & Assert — second sync with same payload hash
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.added").value(0))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.deprecated").value(0));
    }

    @Test
    void sync_WithPermissionOutsidePrefix_ShouldReturn403() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleName = "rh-" + suffix;
        String allowedPrefix = "rh-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);

        String syncPayload = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-prefix-violation",
                  "permissions": [
                    {"code": "financeiro.payments.create", "description": "Create payments"}
                  ]
                }
                """.formatted(moduleInfo.moduleId);

        // Act & Assert
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void sync_WithoutDescription_ShouldReturn422() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleName = "logistica-" + suffix;
        String allowedPrefix = "logistica-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);

        String syncPayload = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-no-desc",
                  "permissions": [
                    {"code": "%s.routes.create", "description": ""}
                  ]
                }
                """.formatted(moduleInfo.moduleId, allowedPrefix);

        // Act & Assert
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Validation error"));
    }

    @Test
    void sync_WithModuleIdMismatch_ShouldReturn403() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleName = "financeiro-" + suffix;
        String allowedPrefix = "financeiro-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);

        String syncPayload = """
                {
                  "moduleId": "00000000-0000-0000-0000-000000000999",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-module-mismatch",
                  "permissions": [
                    {"code": "%s.payments.read", "description": "Read payments"}
                  ]
                }
                """.formatted(allowedPrefix);

        // Act & Assert
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syncPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void sync_WithWrongAuthenticationType_ShouldReturn401() throws Exception {
        // Act & Assert
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-admin")
                                .claim("roles", List.of("PLATFORM_ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "00000000-0000-0000-0000-000000000001",
                                  "schemaVersion": "1.0",
                                  "payloadHash": "hash-jwt-auth",
                                  "permissions": [
                                    {"code": "test.resource.action", "description": "Test"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void sync_WithoutAuthentication_ShouldReturn401() throws Exception {
        // Act & Assert
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "moduleId": "00000000-0000-0000-0000-000000000001",
                                  "schemaVersion": "1.0",
                                  "payloadHash": "hash-unauth",
                                  "permissions": [
                                    {"code": "test.resource.action", "description": "Test"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sync_WithDeprecation_ShouldMarkRemovedPermissions() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleName = "crm-" + suffix;
        String allowedPrefix = "crm-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);

        // First sync with two permissions
        String firstSync = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-first-%s",
                  "permissions": [
                    {"code": "%s.contacts.create", "description": "Create contacts"},
                    {"code": "%s.contacts.delete", "description": "Delete contacts"}
                  ]
                }
                """.formatted(moduleInfo.moduleId, suffix, allowedPrefix, allowedPrefix);

        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstSync))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(2));

        // Second sync without delete permission — it should be deprecated
        String secondSync = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-second-%s",
                  "permissions": [
                    {"code": "%s.contacts.create", "description": "Create contacts"}
                  ]
                }
                """.formatted(moduleInfo.moduleId, suffix, allowedPrefix);

        // Act & Assert
        mockMvc.perform(post(SYNC_ENDPOINT)
                        .header("Authorization", "Bearer " + moduleInfo.secret)
                        .header("X-Module-Id", moduleInfo.moduleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondSync))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.deprecated").value(1));
    }

    @Test
    void sync_WithConcurrentIdenticalPayloads_ShouldProcessOnceAndReturnIdempotentResponses() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleName = "concurrent-" + suffix;
        String allowedPrefix = "concurrent-" + suffix;
        ModuleInfo moduleInfo = createModuleAndGetKey(moduleName, allowedPrefix);
        UUID moduleId = UUID.fromString(moduleInfo.moduleId);

        String syncPayload = """
                {
                  "moduleId": "%s",
                  "schemaVersion": "1.0",
                  "payloadHash": "hash-concurrent-%s",
                  "permissions": [
                    {"code": "%s.orders.create", "description": "Create orders"}
                  ]
                }
                """.formatted(moduleInfo.moduleId, suffix, allowedPrefix);

        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<String>> futures = List.of(
                    submitConcurrentSync(executor, readyLatch, startLatch, moduleInfo, syncPayload),
                    submitConcurrentSync(executor, readyLatch, startLatch, moduleInfo, syncPayload)
            );

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            List<String> responses = futures.stream()
                    .map(this::getResponseBody)
                    .toList();

            long changedTrueCount = responses.stream()
                    .filter(body -> Boolean.TRUE.equals(JsonPath.read(body, "$.changed")))
                    .count();
            long changedFalseCount = responses.stream()
                    .filter(body -> Boolean.FALSE.equals(JsonPath.read(body, "$.changed")))
                    .count();

            assertThat(changedTrueCount).isEqualTo(1);
            assertThat(changedFalseCount).isEqualTo(1);
            assertThat(permissionRepository.countByModuleId(moduleId)).isEqualTo(1);
            assertThat(syncEventRepository.countByModuleId(moduleId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    // --- Helpers ---

    private Future<String> submitConcurrentSync(
            ExecutorService executor,
            CountDownLatch readyLatch,
            CountDownLatch startLatch,
            ModuleInfo moduleInfo,
            String syncPayload
    ) {
        return executor.submit(() -> {
            readyLatch.countDown();
            if (!startLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent sync");
            }
            return mockMvc.perform(post(SYNC_ENDPOINT)
                            .header("Authorization", "Bearer " + moduleInfo.secret)
                            .header("X-Module-Id", moduleInfo.moduleId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(syncPayload))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        });
    }

    private String getResponseBody(Future<String> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to collect concurrent sync response", exception);
        }
    }

    private ModuleInfo createModuleAndGetKey(String name, String allowedPrefix) throws Exception {
        String createResponse = mockMvc.perform(post(MODULES_ENDPOINT)
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

        String moduleId = JsonPath.read(createResponse, "$.moduleId");
        String secret = JsonPath.read(createResponse, "$.secret");

        return new ModuleInfo(moduleId, secret);
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor platformAdminJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("user-admin")
                .claim("roles", List.of("PLATFORM_ADMIN")));
    }

    private record ModuleInfo(String moduleId, String secret) {
    }
}
