package com.platform.authz.sdk.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.authz.sdk.AuthzAccessTokenProvider;
import com.platform.authz.sdk.AuthzClientImpl;
import com.platform.authz.sdk.AuthzProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelfRegistrationRunnerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("run should sync successfully on the first attempt and open readiness gate")
    void run_WhenSyncSucceedsOnFirstAttempt_ShouldMarkGateReady() throws Exception {
        // Arrange
        PermissionsYamlLoader loader = loaderFor(
                """
                schemaVersion: "1.0"
                moduleId: "vendas"
                permissions:
                  - code: "vendas.orders.create"
                    description: "Criar pedidos"
                """
        );
        ReadinessGate readinessGate = new ReadinessGate();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(OBJECT_MAPPER.writeValueAsString(Map.of(
                        "catalogVersion", "catalog-v1",
                        "added", 1,
                        "updated", 0,
                        "deprecated", 0,
                        "changed", true
                ))));
        SelfRegistrationRunner runner = createRunner(loader, readinessGate);

        // Act
        runner.run(new DefaultApplicationArguments(new String[0]));

        // Assert
        assertThat(readinessGate.isReady()).isTrue();
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);

        RecordedRequest request = mockWebServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer module-secret");
        assertThat(request.getPath()).isEqualTo("/v1/catalog/sync");
        assertThat(requestBody).contains("\"moduleId\":\"vendas\"");
        assertThat(requestBody).contains("\"payloadHash\":\"" + expectedHash(loader) + "\"");
    }

    @Test
    @DisplayName("run should retry on transient failure and eventually mark readiness gate as up")
    void run_WhenSyncFailsTransiently_ShouldRetryAndSucceed() throws Exception {
        // Arrange
        PermissionsYamlLoader loader = loaderFor(
                """
                schemaVersion: "1.0"
                moduleId: "vendas"
                permissions:
                  - code: "vendas.orders.create"
                    description: "Criar pedidos"
                """
        );
        ReadinessGate readinessGate = new ReadinessGate();
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(OBJECT_MAPPER.writeValueAsString(Map.of(
                        "catalogVersion", "catalog-v2",
                        "added", 0,
                        "updated", 1,
                        "deprecated", 0,
                        "changed", true
                ))));
        SelfRegistrationRunner runner = createRunner(loader, readinessGate);

        // Act
        runner.run(new DefaultApplicationArguments(new String[0]));

        // Assert
        assertThat(readinessGate.isReady()).isTrue();
        assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("run should stop on terminal unauthorized failure and keep readiness gate down")
    void run_WhenSyncFailsWithUnauthorized_ShouldKeepGateDown() {
        // Arrange
        PermissionsYamlLoader loader = loaderFor(
                """
                schemaVersion: "1.0"
                moduleId: "vendas"
                permissions:
                  - code: "vendas.orders.create"
                    description: "Criar pedidos"
                """
        );
        ReadinessGate readinessGate = new ReadinessGate();
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));
        SelfRegistrationRunner runner = createRunner(loader, readinessGate);

        // Act
        runner.run(new DefaultApplicationArguments(new String[0]));

        // Assert
        assertThat(readinessGate.isReady()).isFalse();
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("run should fail fast when permissions payload is invalid")
    void run_WhenPermissionsPayloadIsInvalid_ShouldFailStartup() {
        // Arrange
        PermissionsYamlLoader loader = new PermissionsYamlLoader(
                new InMemoryResourceLoader(Map.of(
                        "classpath:permissions.yaml",
                        """
                        schemaVersion: "1.0"
                        moduleId: "vendas"
                        permissions:
                          - code: "vendas.orders.create"
                        """
                )),
                registrationProperties()
        );
        ReadinessGate readinessGate = new ReadinessGate();
        SelfRegistrationRunner runner = createRunner(loader, readinessGate);

        // Act / Assert
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(PermissionsYamlLoader.InvalidPermissionsFileException.class)
                .hasMessageContaining("permissions[0].description");
        assertThat(readinessGate.isReady()).isFalse();
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    private SelfRegistrationRunner createRunner(PermissionsYamlLoader loader, ReadinessGate readinessGate) {
        return new SelfRegistrationRunner(
                createClient(),
                loader,
                readinessGate,
                RetryRegistry.ofDefaults(),
                Duration.ofMillis(1),
                Duration.ofMillis(2),
                Duration.ofMillis(10)
        );
    }

    private AuthzClientImpl createClient() {
        AuthzProperties properties = new AuthzProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());
        properties.setModuleKey("module-secret");
        properties.setModuleId("vendas");
        properties.setTimeout(Duration.ofSeconds(2));

        HttpClient httpClient = HttpClient.create().responseTimeout(properties.getTimeout());
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults())
                .circuitBreaker("authz");
        AuthzAccessTokenProvider accessTokenProvider = java.util.Optional::empty;
        return new AuthzClientImpl(webClient, properties, accessTokenProvider, RetryRegistry.ofDefaults().retry("authz"), circuitBreaker);
    }

    private PermissionsYamlLoader loaderFor(String yaml) {
        return new PermissionsYamlLoader(
                new InMemoryResourceLoader(Map.of("classpath:permissions.yaml", yaml)),
                registrationProperties()
        );
    }

    private String expectedHash(PermissionsYamlLoader loader) {
        try {
            String canonical = loader.canonicalize(loader.load());
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private RegistrationProperties registrationProperties() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setPermissionsFile("permissions.yaml");
        return properties;
    }

    private static final class InMemoryResourceLoader implements ResourceLoader {

        private final Map<String, String> resources;

        private InMemoryResourceLoader(Map<String, String> resources) {
            this.resources = resources;
        }

        @Override
        public Resource getResource(String location) {
            String content = resources.get(location);
            if (content == null) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }

            return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getDescription() {
                    return location;
                }
            };
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
