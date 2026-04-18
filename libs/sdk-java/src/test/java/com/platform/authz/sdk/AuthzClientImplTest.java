package com.platform.authz.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.authz.sdk.dto.SyncRequest;
import com.platform.authz.sdk.dto.SyncResult;
import com.platform.authz.sdk.exception.AuthzClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthzClientImplTest {

    private static final String TEST_ACCESS_TOKEN = "test-user-jwt";

    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("fetchUserPermissions")
    class FetchUserPermissions {

        @Test
        @DisplayName("should return permissions on 200 OK and propagate bearer token")
        void fetchUserPermissions_Success_PropagatesBearerToken() throws Exception {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            Map<String, Object> responseBody = Map.of(
                    "userId", "user-1",
                    "permissions", List.of("orders.read", "orders.write", "products.read"),
                    "resolvedAt", Instant.now().toString(),
                    "ttlSeconds", 600
            );

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(responseBody)));

            // Act
            Set<String> permissions = authzClient.fetchUserPermissions("user-1");

            // Assert
            assertThat(permissions)
                    .containsExactlyInAnyOrder("orders.read", "orders.write", "products.read");

            RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/v1/users/user-1/permissions");
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TEST_ACCESS_TOKEN);
            assertThat(request.getHeader("X-Module-Key")).isNull();
        }

        @Test
        @DisplayName("should throw AuthzClientException on 401 Unauthorized without retry")
        void fetchUserPermissions_Unauthorized_DoesNotRetry() {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\":\"unauthorized\"}"));

            // Act & Assert
            assertThatThrownBy(() -> authzClient.fetchUserPermissions("user-1"))
                    .isInstanceOf(AuthzClientException.class)
                    .satisfies(ex -> {
                        AuthzClientException ace = (AuthzClientException) ex;
                        assertThat(ace.getStatusCode()).isEqualTo(401);
                        assertThat(ace.isClientError()).isTrue();
                    });
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should retry on 503 and eventually succeed")
        void fetchUserPermissions_ServiceUnavailable_RetriesAndSucceeds() throws Exception {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            Map<String, Object> responseBody = Map.of(
                    "userId", "user-1",
                    "permissions", List.of("orders.read"),
                    "resolvedAt", Instant.now().toString(),
                    "ttlSeconds", 600
            );

            mockWebServer.enqueue(new MockResponse().setResponseCode(503));
            mockWebServer.enqueue(new MockResponse().setResponseCode(503));
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(responseBody)));

            // Act
            Set<String> permissions = authzClient.fetchUserPermissions("user-1");

            // Assert
            assertThat(permissions).containsExactly("orders.read");
            assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw on timeout")
        void fetchUserPermissions_Timeout_ThrowsException() {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"userId\":\"user-1\",\"permissions\":[],\"resolvedAt\":\"2024-01-01T00:00:00Z\",\"ttlSeconds\":600}")
                    .setBodyDelay(5, TimeUnit.SECONDS));

            // Act & Assert
            assertThatThrownBy(() -> authzClient.fetchUserPermissions("user-1"))
                    .isInstanceOf(AuthzClientException.class);
        }

        @Test
        @DisplayName("should fail when no runtime access token is available")
        void fetchUserPermissions_WithoutAccessToken_ThrowsException() {
            // Arrange
            AuthzClientImpl authzClient = createClient(() -> java.util.Optional.empty(), createRetry(), createCircuitBreaker());

            // Act & Assert
            assertThatThrownBy(() -> authzClient.fetchUserPermissions("user-1"))
                    .isInstanceOf(AuthzClientException.class)
                    .hasMessageContaining("No bearer access token available");
            assertThat(mockWebServer.getRequestCount()).isZero();
        }
    }

    @Nested
    @DisplayName("check")
    class Check {

        @Test
        @DisplayName("should return true when permission is allowed")
        void check_Allowed_ReturnsTrue() throws Exception {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            Map<String, Object> responseBody = Map.of(
                    "allowed", true,
                    "source", "cache"
            );

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(responseBody)));

            // Act
            boolean result = authzClient.check("user-1", "orders.read");

            // Assert
            assertThat(result).isTrue();

            RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/v1/authz/check");
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + TEST_ACCESS_TOKEN);
            assertThat(request.getHeader("X-Module-Key")).isNull();

            String body = request.getBody().readUtf8();
            assertThat(body).contains("\"userId\":\"user-1\"");
            assertThat(body).contains("\"permission\":\"orders.read\"");
        }

        @Test
        @DisplayName("should return false when permission is denied")
        void check_Denied_ReturnsFalse() throws Exception {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            Map<String, Object> responseBody = Map.of(
                    "allowed", false,
                    "source", "database"
            );

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(responseBody)));

            // Act
            boolean result = authzClient.check("user-1", "orders.delete");

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should retry on 503 and eventually succeed")
        void check_ServiceUnavailable_RetriesAndSucceeds() throws Exception {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            mockWebServer.enqueue(new MockResponse().setResponseCode(503));
            mockWebServer.enqueue(new MockResponse().setResponseCode(503));
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(Map.of("allowed", true, "source", "database"))));

            // Act
            boolean allowed = authzClient.check("user-1", "orders.read");

            // Assert
            assertThat(allowed).isTrue();
            assertThat(mockWebServer.getRequestCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("sync")
    class Sync {

        @Test
        @DisplayName("should return SyncResult on 200 OK")
        void sync_Success_ReturnsSyncResult() throws Exception {
            // Arrange
            AuthzClientImpl authzClient = createClient();
            Map<String, Object> responseBody = Map.of(
                    "catalogVersion", "abc12345-1700000000000",
                    "added", 2,
                    "updated", 1,
                    "deprecated", 0,
                    "changed", true
            );

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(responseBody)));

            SyncRequest request = new SyncRequest(
                    "module-1",
                    "1.0.0",
                    "hash123",
                    List.of(
                            new SyncRequest.PermissionDeclaration("mod.users.read", "Read users"),
                            new SyncRequest.PermissionDeclaration("mod.users.write", "Write users")
                    )
            );

            // Act
            SyncResult result = authzClient.sync(request);

            // Assert
            assertThat(result.catalogVersion()).isEqualTo("abc12345-1700000000000");
            assertThat(result.added()).isEqualTo(2);
            assertThat(result.updated()).isEqualTo(1);
            assertThat(result.deprecated()).isZero();
            assertThat(result.changed()).isTrue();

            RecordedRequest recorded = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
            assertThat(recorded).isNotNull();
            assertThat(recorded.getPath()).isEqualTo("/v1/catalog/sync");
            assertThat(recorded.getMethod()).isEqualTo("POST");
            assertThat(recorded.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-module-key");
            assertThat(recorded.getHeader("X-Module-Id")).isEqualTo("test-module-id");
        }
    }

    @Test
    @DisplayName("should open circuit breaker after repeated failures")
    void fetchUserPermissions_WhenCircuitOpens_ShouldShortCircuitNextCall() {
        // Arrange
        CircuitBreaker circuitBreaker = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .build()).circuitBreaker("authz");
        AuthzClientImpl authzClient = createClient(() -> java.util.Optional.of(TEST_ACCESS_TOKEN), createSingleAttemptRetry(), circuitBreaker);

        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        // Act
        assertThatThrownBy(() -> authzClient.fetchUserPermissions("user-1"))
                .isInstanceOf(AuthzClientException.class)
                .satisfies(ex -> assertThat(((AuthzClientException) ex).getStatusCode()).isEqualTo(503));
        assertThatThrownBy(() -> authzClient.fetchUserPermissions("user-1"))
                .isInstanceOf(AuthzClientException.class)
                .satisfies(ex -> assertThat(((AuthzClientException) ex).getStatusCode()).isEqualTo(503));
        assertThatThrownBy(() -> authzClient.fetchUserPermissions("user-1"))
                .isInstanceOf(AuthzClientException.class)
                .hasMessageContaining("circuit breaker is open");

        // Assert
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    private AuthzClientImpl createClient() {
        return createClient(() -> java.util.Optional.of(TEST_ACCESS_TOKEN), createRetry(), createCircuitBreaker());
    }

    private AuthzClientImpl createClient(
            AuthzAccessTokenProvider accessTokenProvider,
            Retry retry,
            CircuitBreaker circuitBreaker
    ) {
        AuthzProperties properties = new AuthzProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());
        properties.setModuleKey("test-module-key");
        properties.setModuleId("test-module-id");
        properties.setTimeout(Duration.ofSeconds(2));

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(properties.getTimeout());

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        return new AuthzClientImpl(webClient, properties, accessTokenProvider, retry, circuitBreaker);
    }

    private Retry createRetry() {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryOnException(throwable -> {
                    if (throwable instanceof AuthzClientException authzClientException) {
                        return authzClientException.isServerError() || authzClientException.getStatusCode() == 0;
                    }
                    return true;
                })
                .build();
        return RetryRegistry.of(retryConfig).retry("authz");
    }

    private Retry createSingleAttemptRetry() {
        return RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(1)
                .build()).retry("authz");
    }

    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(10)
                .slidingWindowSize(10)
                .recordException(throwable -> {
                    if (throwable instanceof AuthzClientException authzClientException) {
                        return authzClientException.isServerError() || authzClientException.getStatusCode() == 0;
                    }
                    return true;
                })
                .build();
        return CircuitBreakerRegistry.of(circuitBreakerConfig).circuitBreaker("authz");
    }
}
