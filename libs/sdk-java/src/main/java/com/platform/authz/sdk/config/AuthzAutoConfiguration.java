package com.platform.authz.sdk.config;

import com.platform.authz.sdk.AuthzClient;
import com.platform.authz.sdk.AuthzAccessTokenProvider;
import com.platform.authz.sdk.AuthzClientImpl;
import com.platform.authz.sdk.AuthzProperties;
import com.platform.authz.sdk.RequestContextAccessTokenProvider;
import com.platform.authz.sdk.aop.HasPermissionAspect;
import com.platform.authz.sdk.aop.PermissionMatcher;
import com.platform.authz.sdk.cache.RequestScopedPermissionCache;
import com.platform.authz.sdk.exception.AuthzClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryRegistry;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Auto-configuration for the AuthZ SDK.
 *
 * <p>Registers {@link AuthzClient}, a dedicated {@link WebClient}, and
 * a request-scoped {@link RequestScopedPermissionCache}.
 *
 * <p>Activated by default; disable with {@code authz.enabled=false}.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuthzProperties.class)
@ConditionalOnProperty(prefix = "authz", name = "enabled", matchIfMissing = true)
public class AuthzAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthzAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "authzWebClient")
    public WebClient authzWebClient(AuthzProperties properties) {
        Objects.requireNonNull(properties.getBaseUrl(), "authz.base-url must be configured");

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getTimeout().toMillis())
                .responseTimeout(properties.getTimeout());

        LOGGER.info("Configuring AuthZ WebClient baseUrl={} timeout={}",
                properties.getBaseUrl(), properties.getTimeout());

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(AuthzClient.class)
    public AuthzClient authzClient(
            WebClient authzWebClient,
            AuthzProperties properties,
            AuthzAccessTokenProvider authzAccessTokenProvider,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new AuthzClientImpl(
                authzWebClient,
                properties,
                authzAccessTokenProvider,
                retryRegistry.retry("authz"),
                circuitBreakerRegistry.circuitBreaker("authz")
        );
    }

    @Bean
    @ConditionalOnMissingBean(AuthzAccessTokenProvider.class)
    public AuthzAccessTokenProvider authzAccessTokenProvider() {
        return new RequestContextAccessTokenProvider();
    }

    @Bean
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    @ConditionalOnMissingBean(RequestScopedPermissionCache.class)
    public RequestScopedPermissionCache requestScopedPermissionCache(AuthzClient authzClient) {
        return new RequestScopedPermissionCache(authzClient);
    }

    @Bean
    @ConditionalOnMissingBean(PermissionMatcher.class)
    public PermissionMatcher permissionMatcher() {
        return new PermissionMatcher();
    }

    @Bean
    @ConditionalOnMissingBean(HasPermissionAspect.class)
    public HasPermissionAspect hasPermissionAspect(
            RequestScopedPermissionCache requestScopedPermissionCache,
            PermissionMatcher permissionMatcher
    ) {
        return new HasPermissionAspect(requestScopedPermissionCache, permissionMatcher);
    }

    @Bean
    @ConditionalOnMissingBean(name = "authzRetryConfigCustomizer")
    public RetryConfigCustomizer authzRetryConfigCustomizer() {
        return RetryConfigCustomizer.of("authz", builder -> builder
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(200), 2.0))
                .retryOnException(throwable -> isRetryableException((Throwable) throwable))
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "authzCircuitBreakerConfigCustomizer")
    public CircuitBreakerConfigCustomizer authzCircuitBreakerConfigCustomizer() {
        return CircuitBreakerConfigCustomizer.of("authz", builder -> builder
                .failureRateThreshold(50.0f)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(10)
                .recordException(throwable -> shouldRecordAsFailure((Throwable) throwable))
        );
    }

    private static boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return false;
        }

        if (throwable instanceof AuthzClientException authzClientException) {
            return authzClientException.isServerError() || authzClientException.getStatusCode() == 0;
        }

        return true;
    }

    private static boolean shouldRecordAsFailure(Throwable throwable) {
        if (throwable instanceof AuthzClientException authzClientException) {
            return authzClientException.isServerError() || authzClientException.getStatusCode() == 0;
        }

        return true;
    }
}
