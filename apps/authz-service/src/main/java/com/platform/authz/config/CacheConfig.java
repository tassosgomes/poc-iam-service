package com.platform.authz.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_PERMISSIONS_CACHE = "userPermissions";

    @Bean
    public CacheManager cacheManager(
            AuthzCacheProperties cacheProperties,
            AuthzSecurityProperties securityProperties,
            MeterRegistry meterRegistry
    ) {
        Objects.requireNonNull(cacheProperties, "cacheProperties must not be null");
        Objects.requireNonNull(securityProperties, "securityProperties must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");

        Duration userPermissionsTtl = cacheProperties.userPermissionsTtl();
        Duration jwtTtl = securityProperties.jwtTtl();
        validateCacheTtl(userPermissionsTtl, jwtTtl);

        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = Caffeine.newBuilder()
                .expireAfterWrite(userPermissionsTtl)
                .maximumSize(cacheProperties.userPermissionsMaxSize())
                .recordStats()
                .build();

        CaffeineCacheMetrics.monitor(meterRegistry, nativeCache, USER_PERMISSIONS_CACHE);
        Gauge.builder("authz_user_permissions_cache_hit_ratio", nativeCache, cache ->
                        cache.stats().requestCount() == 0 ? 0.0d : cache.stats().hitRate())
                .description("Cache hit ratio for user permissions bulk fetch")
                .tag("cache", USER_PERMISSIONS_CACHE)
                .register(meterRegistry);

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(
                USER_PERMISSIONS_CACHE,
                nativeCache
        );
        return cacheManager;
    }

    private void validateCacheTtl(Duration userPermissionsTtl, Duration jwtTtl) {
        if (userPermissionsTtl.isNegative() || userPermissionsTtl.isZero()) {
            throw new IllegalStateException("authz.cache.user-permissions-ttl must be greater than zero");
        }

        if (jwtTtl.isNegative() || jwtTtl.isZero()) {
            throw new IllegalStateException("authz.security.jwt-ttl must be greater than zero");
        }

        if (userPermissionsTtl.compareTo(jwtTtl) > 0) {
            throw new IllegalStateException(
                    "authz.cache.user-permissions-ttl must be less than or equal to authz.security.jwt-ttl"
            );
        }
    }
}
