package com.platform.authz.iam.infra;

import com.platform.authz.iam.application.UserSearchPort;
import com.platform.authz.iam.application.UserSummary;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Component
public class CyberArkUserSearchClient implements UserSearchPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(CyberArkUserSearchClient.class);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(2);

    private final WebClient webClient;

    @Autowired
    public CyberArkUserSearchClient(CyberArkProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        HttpClient httpClient = HttpClient.create().responseTimeout(RESPONSE_TIMEOUT);
        this.webClient = WebClient.builder()
                .baseUrl(properties.userApiBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    CyberArkUserSearchClient(WebClient webClient) {
        this.webClient = Objects.requireNonNull(webClient, "webClient must not be null");
    }

    @Override
    @CircuitBreaker(name = "cyberark", fallbackMethod = "searchUsersFallback")
    @Retry(name = "cyberark")
    public List<UserSummary> searchUsers(String query, String moduleFilter) {
        Objects.requireNonNull(query, "query must not be null");

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParamIfPresent("moduleId", Optional.ofNullable(moduleFilter))
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.createException().flatMap(reactor.core.publisher.Mono::error))
                .bodyToMono(new ParameterizedTypeReference<List<UserSummary>>() {})
                .defaultIfEmpty(List.of())
                .block();
    }

    @SuppressWarnings("unused")
    private List<UserSummary> searchUsersFallback(String query, String moduleFilter, Throwable exception) {
        LOGGER.warn(
                "cyberark_user_search_fallback module_filter={} reason={}",
                moduleFilter != null ? moduleFilter : "none",
                exception.getClass().getSimpleName()
        );
        throw new CyberArkUnavailableException("CyberArk user search service is unavailable", exception);
    }
}
