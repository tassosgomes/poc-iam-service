package com.platform.authz.modules.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ModuleAdminIntegrationTest {
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

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void createAndRotateKey_WithPlatformAdminRole_ShouldReturnPlainSecretsAndModuleSummary() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String name = "Sales-" + suffix;
        String prefix = "sales-" + suffix;

        // Act & Assert — create module
        String createResponse = mockMvc.perform(post(MODULES_ENDPOINT)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "allowedPrefix": "%s",
                                  "description": "Sales module"
                                }
                                """.formatted(name, prefix)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/v1/modules/[a-f0-9\\-]+")))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.allowedPrefix").value(prefix))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String moduleId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.moduleId");

        // Assert — get module summary
        mockMvc.perform(get("/v1/modules/{id}", moduleId)
                        .with(platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleId").value(moduleId))
                .andExpect(jsonPath("$.heartbeatStatus").value("NEVER_REPORTED"))
                .andExpect(jsonPath("$.activeKeyStatus").value("ACTIVE"));

        // Act & Assert — rotate key
        mockMvc.perform(post("/v1/modules/{id}/keys/rotate", moduleId)
                        .with(platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleId").value(moduleId))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.graceExpiresAt").isNotEmpty());

        // Assert — list modules contains created module
        mockMvc.perform(get(MODULES_ENDPOINT)
                        .with(platformAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.moduleId == '%s')]".formatted(moduleId)).exists());
    }

    @Test
    void createModule_WithDuplicatedAllowedPrefix_ShouldReturnConflictProblemDetail() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String payload = """
                {
                  "name": "Support-%s",
                  "allowedPrefix": "support-%s",
                  "description": "Support module"
                }
                """.formatted(suffix, suffix);

        mockMvc.perform(post(MODULES_ENDPOINT)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Act & Assert — duplicate prefix
        mockMvc.perform(post(MODULES_ENDPOINT)
                        .with(platformAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Module conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createModule_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();

        // Act & Assert
        mockMvc.perform(post(MODULES_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anon-%s",
                                  "allowedPrefix": "anon-%s",
                                  "description": "Anon module"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createModule_WithoutPlatformAdminRole_ShouldReturnForbidden() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();

        // Act & Assert
        mockMvc.perform(post(MODULES_ENDPOINT)
                        .with(nonAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Ops-%s",
                                  "allowedPrefix": "ops-%s",
                                  "description": "Ops module"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rotateKey_WithoutAuthentication_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleId = createModuleAsPlatformAdmin("Auth-" + suffix, "auth-" + suffix);

        // Act & Assert
        mockMvc.perform(post("/v1/modules/{id}/keys/rotate", moduleId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotateKey_WithoutPlatformAdminRole_ShouldReturnForbidden() throws Exception {
        // Arrange
        String suffix = uniqueSuffix();
        String moduleId = createModuleAsPlatformAdmin("Billing-" + suffix, "billing-" + suffix);

        // Act & Assert
        mockMvc.perform(post("/v1/modules/{id}/keys/rotate", moduleId)
                        .with(nonAdminJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403));
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor platformAdminJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("user-admin")
                .claim("roles", java.util.List.of("PLATFORM_ADMIN")));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor nonAdminJwt() {
        return jwt().jwt(jwt -> jwt
                .subject("user-operator")
                .claim("roles", java.util.List.of("AUDITOR")));
    }

    private String createModuleAsPlatformAdmin(String name, String allowedPrefix) throws Exception {
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

        return com.jayway.jsonpath.JsonPath.read(response, "$.moduleId");
    }
}
