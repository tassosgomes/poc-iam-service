package com.platform.authz.sdk;

import com.platform.authz.sdk.dto.CheckPermissionRequest;
import com.platform.authz.sdk.dto.CheckPermissionResponse;
import com.platform.authz.sdk.dto.SyncRequest;
import com.platform.authz.sdk.dto.SyncResult;
import com.platform.authz.sdk.dto.UserPermissionsResponse;
import com.platform.authz.sdk.exception.AuthzClientException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Default {@link AuthzClient} implementation backed by {@link WebClient} and Resilience4j.
 *
 * <p>Runtime endpoints use JWT passthrough, while catalog sync uses module bearer authentication.
 */
public class AuthzClientImpl implements AuthzClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthzClientImpl.class);

    private final WebClient webClient;
    private final AuthzProperties properties;
    private final AuthzAccessTokenProvider accessTokenProvider;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public AuthzClientImpl(
            WebClient webClient,
            AuthzProperties properties,
            AuthzAccessTokenProvider accessTokenProvider,
            Retry retry,
            CircuitBreaker circuitBreaker
    ) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.accessTokenProvider = Objects.requireNonNull(accessTokenProvider, "accessTokenProvider must not be null");
        this.retry = Objects.requireNonNull(retry, "retry must not be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    @Override
    public Set<String> fetchUserPermissions(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        LOGGER.debug("Fetching permissions for userId={}", userId);

        UserPermissionsResponse response = executeWithResilience(() -> webClient.get()
                .uri("/v1/users/{userId}/permissions", userId)
                .headers(this::applyRuntimeAuthHeaders)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AuthzClientException(
                                        "Failed to fetch permissions for userId=" + userId
                                                + " status=" + clientResponse.statusCode().value(),
                                        clientResponse.statusCode().value()
                                )))
                )
                .bodyToMono(UserPermissionsResponse.class)
                .block());

        if (response == null) {
            throw new AuthzClientException("Null response when fetching permissions for userId=" + userId);
        }

        LOGGER.debug("Fetched {} permissions for userId={}", response.permissions().size(), userId);
        return Set.copyOf(response.permissions());
    }

    @Override
    public boolean check(String userId, String permission) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(permission, "permission must not be null");
        LOGGER.debug("Checking permission={} for userId={}", permission, userId);

        CheckPermissionResponse response = executeWithResilience(() -> webClient.post()
                .uri("/v1/authz/check")
                .headers(this::applyRuntimeAuthHeaders)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CheckPermissionRequest(userId, permission))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new AuthzClientException(
                                        "Failed to check permission=" + permission
                                                + " for userId=" + userId
                                                + " status=" + clientResponse.statusCode().value(),
                                        clientResponse.statusCode().value()
                                )))
                )
                .bodyToMono(CheckPermissionResponse.class)
                .block());

        if (response == null) {
            throw new AuthzClientException(
                    "Null response when checking permission=" + permission + " for userId=" + userId
            );
        }

        LOGGER.debug("Check result: permission={} userId={} allowed={}", permission, userId, response.allowed());
        return response.allowed();
    }

    @Override
    public SyncResult sync(SyncRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        LOGGER.debug("Syncing catalog for moduleId={}", request.moduleId());

        SyncResult response;
        try {
            response = webClient.post()
                    .uri("/v1/catalog/sync")
                    .headers(this::applyModuleSyncHeaders)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body -> Mono.error(new AuthzClientException(
                                            "Failed to sync catalog for moduleId=" + request.moduleId()
                                                    + " status=" + clientResponse.statusCode().value(),
                                            clientResponse.statusCode().value()
                                    )))
                    )
                    .bodyToMono(SyncResult.class)
                    .block();
        } catch (AuthzClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthzClientException(
                    "AuthZ catalog sync call failed for moduleId=" + request.moduleId(),
                    exception
            );
        }

        if (response == null) {
            throw new AuthzClientException(
                    "Null response when syncing catalog for moduleId=" + request.moduleId()
            );
        }

        LOGGER.debug("Sync completed: moduleId={} added={} updated={} deprecated={} changed={}",
                request.moduleId(), response.added(), response.updated(),
                response.deprecated(), response.changed());
        return response;
    }

    private <T> T executeWithResilience(Supplier<T> supplier) {
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(
                retry,
                CircuitBreaker.decorateSupplier(circuitBreaker, supplier)
        );

        try {
            return decoratedSupplier.get();
        } catch (CallNotPermittedException exception) {
            throw new AuthzClientException("AuthZ circuit breaker is open", exception);
        } catch (AuthzClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AuthzClientException("AuthZ client call failed", exception);
        }
    }

    private void applyRuntimeAuthHeaders(HttpHeaders headers) {
        headers.setBearerAuth(resolveAccessToken());
    }

    private String resolveAccessToken() {
        return accessTokenProvider.resolveAccessToken()
                .filter(token -> !token.isBlank())
                .orElseThrow(() -> new AuthzClientException(
                        "No bearer access token available for AuthZ runtime endpoint"
                ));
    }

    private void applyModuleSyncHeaders(HttpHeaders headers) {
        String moduleKey = properties.getModuleKey();
        String moduleId = properties.getModuleId();

        if (moduleKey != null && !moduleKey.isBlank()) {
            headers.setBearerAuth(moduleKey);
        }

        if (moduleId != null && !moduleId.isBlank()) {
            headers.set("X-Module-Id", moduleId);
        }
    }
}
