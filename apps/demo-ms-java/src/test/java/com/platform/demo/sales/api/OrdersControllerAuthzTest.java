package com.platform.demo.sales.api;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.authz.sdk.AuthzClient;
import com.platform.demo.sales.SalesApplication;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = SalesApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "authz.enabled=true",
                "authz.base-url=http://localhost:9999",
                "authz.module-id=vendas",
                "authz.module-key=test-key",
                "authz.registration.enabled=true"
        }
)
@AutoConfigureMockMvc
class OrdersControllerAuthzTest {

    private static final String USER_ID = "user-vendas-op";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthzClient authzClient;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        reset(authzClient);
    }

    @Test
    @DisplayName("GET /orders returns 401 when no JWT is provided")
    void listOrders_NoJwt_Returns401() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authzClient);
    }

    @Test
    @DisplayName("GET /orders returns 403 when JWT is valid but permission is missing")
    void listOrders_WithJwtAndMissingPermission_Returns403() throws Exception {
        // Arrange
        when(authzClient.fetchUserPermissions(USER_ID)).thenReturn(Set.of());

        // Act & Assert
        mockMvc.perform(get("/orders")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID))))
                .andExpect(status().isForbidden());

        verify(authzClient).fetchUserPermissions(USER_ID);
    }

    @Test
    @DisplayName("GET /orders returns 200 when JWT is valid and permission is granted")
    void listOrders_WithJwtAndPermission_Returns200() throws Exception {
        // Arrange
        when(authzClient.fetchUserPermissions(USER_ID)).thenReturn(Set.of("vendas.orders.view"));

        // Act & Assert
        mockMvc.perform(get("/orders")
                        .with(jwt().jwt(jwt -> jwt.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(authzClient).fetchUserPermissions(USER_ID);
    }

    @Test
    @DisplayName("GET /.well-known/permissions returns 200 without authentication")
    void permissionsDiscovery_NoAuth_Returns200() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/.well-known/permissions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.moduleId").value("vendas"))
                .andExpect(jsonPath("$.schemaVersion").value("1.0"))
                .andExpect(jsonPath("$.permissions.length()").value(4));

        verifyNoInteractions(authzClient);
    }
}
