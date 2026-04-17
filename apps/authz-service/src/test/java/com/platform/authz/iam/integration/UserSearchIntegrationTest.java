package com.platform.authz.iam.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class UserSearchIntegrationTest {
    private static final String SEARCH_ENDPOINT = "/v1/users/search";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final AtomicReference<UserApiMode> USER_API_MODE = new AtomicReference<>(UserApiMode.SUCCESS);
    private static final HttpServer cyberArkUserApiServer = startUserApiServer();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authz")
            .withUsername("authz")
            .withPassword("authz");

    @Container
    static GenericContainer<?> mockOauth2Server = new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:2.1.1")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("cyberark-mock/users.json"),
                    "/config.json"
            )
            .withEnv("JSON_CONFIG_PATH", "/config.json")
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTHZ_DB_URL", postgres::getJdbcUrl);
        registry.add("AUTHZ_DB_USER", postgres::getUsername);
        registry.add("AUTHZ_DB_PASS", postgres::getPassword);
        registry.add(
                "CYBERARK_ISSUER",
                () -> "http://%s:%d/default".formatted(mockOauth2Server.getHost(), mockOauth2Server.getMappedPort(8080))
        );
        registry.add(
                "CYBERARK_USER_API_BASE_URL",
                () -> "http://127.0.0.1:%d/api/users".formatted(cyberArkUserApiServer.getAddress().getPort())
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @AfterAll
    static void stopUserApiServer() {
        if (cyberArkUserApiServer != null) {
            cyberArkUserApiServer.stop(0);
        }
    }

    @Test
    void searchUsers_WithoutAuthentication_ShouldReturn401() throws Exception {
        USER_API_MODE.set(UserApiMode.SUCCESS);

        mockMvc.perform(get(SEARCH_ENDPOINT).param("q", "user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchUsers_WithPlatformAdminToken_ShouldReturnAllUsers() throws Exception {
        USER_API_MODE.set(UserApiMode.SUCCESS);

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .param("q", "user")
                        .header("Authorization", "Bearer " + issueToken("user-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].userId").value("user-vendas-mgr"));
    }

    @Test
    void searchUsers_WithScopedManagerToken_ShouldFilterModulesByScope() throws Exception {
        USER_API_MODE.set(UserApiMode.SUCCESS);

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .param("q", "user")
                        .header("Authorization", "Bearer " + issueToken("user-vendas-mgr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].userId").value("user-vendas-mgr"))
                .andExpect(jsonPath("$[0].modules[0]").value("vendas"))
                .andExpect(jsonPath("$[1].userId").value("user-vendas-op"))
                .andExpect(jsonPath("$[1].modules[0]").value("vendas"))
                .andExpect(jsonPath("$[2].userId").value("user-multi"))
                .andExpect(jsonPath("$[2].modules.length()").value(1))
                .andExpect(jsonPath("$[2].modules[0]").value("vendas"));
    }

    @Test
    void searchUsers_WithScopedManagerSearchingAnotherModule_ShouldReturnEmptyList() throws Exception {
        USER_API_MODE.set(UserApiMode.SUCCESS);

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .param("q", "user")
                        .param("moduleId", "estoque")
                        .header("Authorization", "Bearer " + issueToken("user-vendas-mgr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchUsers_WithModuleFilter_ShouldReturnOnlyMatchingModule() throws Exception {
        USER_API_MODE.set(UserApiMode.SUCCESS);

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .param("q", "user")
                        .param("moduleId", "estoque")
                        .header("Authorization", "Bearer " + issueToken("user-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].modules[0]").value("estoque"))
                .andExpect(jsonPath("$[1].modules[0]").value("estoque"));
    }

    @Test
    void searchUsers_WithOperatorToken_ShouldReturnForbidden() throws Exception {
        USER_API_MODE.set(UserApiMode.SUCCESS);

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .param("q", "user")
                        .header("Authorization", "Bearer " + issueToken("user-vendas-op")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://authz.platform/errors/user-search-forbidden"));
    }

    @Test
    void searchUsers_WhenCyberArkUserApiTimesOut_ShouldReturnServiceUnavailable() throws Exception {
        USER_API_MODE.set(UserApiMode.TIMEOUT);

        mockMvc.perform(get(SEARCH_ENDPOINT)
                        .param("q", "user")
                        .header("Authorization", "Bearer " + issueToken("user-admin")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("https://authz.platform/errors/cyberark-unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }

    private static void handleSearchRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (USER_API_MODE.get() == UserApiMode.TIMEOUT) {
                sleep(Duration.ofMillis(2200));
            }

            String responseBody = """
                    [
                      {"userId":"user-vendas-mgr","displayName":"Vendas Manager","email":"vmgr@demo","modules":["vendas"]},
                      {"userId":"user-vendas-op","displayName":"Vendas Operator","email":"vop@demo","modules":["vendas"]},
                      {"userId":"user-estoque-mgr","displayName":"Estoque Manager","email":"emgr@demo","modules":["estoque"]},
                      {"userId":"user-multi","displayName":"Multi User","email":"multi@demo","modules":["vendas","estoque"]}
                    ]
                    """;
            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(payload);
            }
        }
    }

    private static String issueToken(String username) throws IOException, InterruptedException {
        String tokenEndpoint = "http://%s:%d/default/token".formatted(
                mockOauth2Server.getHost(),
                mockOauth2Server.getMappedPort(8080)
        );
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&username=" + username))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        String accessTokenField = "\"access_token\" : \"";
        int start = response.body().indexOf(accessTokenField);
        if (response.statusCode() != 200 || start < 0) {
            throw new IllegalStateException("Unable to issue test token: " + response.body());
        }

        start += accessTokenField.length();
        int end = response.body().indexOf('"', start);
        return response.body().substring(start, end);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating CyberArk timeout", exception);
        }
    }

    private static HttpServer startUserApiServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/users/search", UserSearchIntegrationTest::handleSearchRequest);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start CyberArk user API stub", exception);
        }
    }

    private enum UserApiMode {
        SUCCESS,
        TIMEOUT
    }
}
