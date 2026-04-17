package com.platform.authz.shared.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.authz.modules.application.ValidateModuleKeyService;
import com.platform.authz.shared.exception.UnauthorizedModuleKeyException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = "SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://issuer.example.test")
@AutoConfigureMockMvc
@Import(ModuleBearerAuthenticationIntegrationTest.TestCatalogController.class)
class ModuleBearerAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ValidateModuleKeyService validateModuleKeyService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void catalogEndpoint_WithValidModuleBearer_ShouldAuthenticateRequest() throws Exception {
        // Arrange
        String moduleId = "98ec60dc-bf84-4540-b863-da1452682f8b";
        when(validateModuleKeyService.validate(moduleId, "valid-secret")).thenReturn(
                new ModuleContext(moduleId, "sales", Instant.parse("2026-04-16T10:00:00Z"))
        );

        // Act & Assert
        mockMvc.perform(post("/v1/catalog/test-sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-secret")
                        .header("X-Module-Id", moduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moduleId").value(moduleId))
                .andExpect(jsonPath("$.allowedPrefix").value("sales"));
    }

    @Test
    void catalogEndpoint_WithInvalidModuleBearer_ShouldReturnProblemDetail() throws Exception {
        // Arrange
        String moduleId = "98ec60dc-bf84-4540-b863-da1452682f8b";
        when(validateModuleKeyService.validate(moduleId, "wrong-secret"))
                .thenThrow(new UnauthorizedModuleKeyException("not_found"));

        // Act & Assert
        mockMvc.perform(post("/v1/catalog/test-sync")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-secret")
                        .header("X-Module-Id", moduleId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://authz.platform/errors/unauthorized-module-key"))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @RestController
    @RequestMapping("/v1/catalog")
    static class TestCatalogController {

        @PostMapping("/test-sync")
        Map<String, String> sync(org.springframework.security.core.Authentication authentication) {
            ModuleContext moduleContext = (ModuleContext) authentication.getPrincipal();
            return Map.of(
                    "moduleId", moduleContext.moduleId(),
                    "allowedPrefix", moduleContext.allowedPrefix()
            );
        }
    }
}
