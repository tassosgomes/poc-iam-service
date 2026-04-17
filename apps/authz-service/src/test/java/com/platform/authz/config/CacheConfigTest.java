package com.platform.authz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCacheManager;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void cacheManager_WithCacheTtlGreaterThanJwtTtl_ShouldFailFast() {
        AuthzCacheProperties cacheProperties = new AuthzCacheProperties(Duration.ofMinutes(16), 10_000L);
        AuthzSecurityProperties securityProperties = new AuthzSecurityProperties(Duration.ofMinutes(15));

        assertThatThrownBy(() -> cacheConfig.cacheManager(cacheProperties, securityProperties, meterRegistry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("authz.cache.user-permissions-ttl must be less than or equal to authz.security.jwt-ttl");
    }

    @Test
    void cacheManager_WithCacheTtlLessThanOrEqualToJwtTtl_ShouldCreateConfiguredCache() {
        AuthzCacheProperties cacheProperties = new AuthzCacheProperties(Duration.ofMinutes(10), 10_000L);
        AuthzSecurityProperties securityProperties = new AuthzSecurityProperties(Duration.ofMinutes(15));

        CaffeineCacheManager cacheManager = (CaffeineCacheManager) cacheConfig.cacheManager(
                cacheProperties,
                securityProperties,
                meterRegistry
        );

        assertThat(cacheManager.getCache(CacheConfig.USER_PERMISSIONS_CACHE)).isNotNull();
    }
}
