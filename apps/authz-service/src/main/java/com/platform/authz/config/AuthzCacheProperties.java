package com.platform.authz.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "authz.cache")
public record AuthzCacheProperties(
        @NotNull Duration userPermissionsTtl,
        @NotNull Long userPermissionsMaxSize
) {
}
