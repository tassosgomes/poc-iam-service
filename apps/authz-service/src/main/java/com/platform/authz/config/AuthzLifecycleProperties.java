package com.platform.authz.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "authz.lifecycle")
public record AuthzLifecycleProperties(
        @NotNull Duration staleAfter,
        @NotBlank String sunsetCron,
        @NotNull Duration detectorRate
) {
    public AuthzLifecycleProperties {
        Objects.requireNonNull(staleAfter, "staleAfter must not be null");
        Objects.requireNonNull(sunsetCron, "sunsetCron must not be null");
        Objects.requireNonNull(detectorRate, "detectorRate must not be null");

        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("authz.lifecycle.stale-after must be greater than zero");
        }

        if (detectorRate.isNegative() || detectorRate.isZero()) {
            throw new IllegalArgumentException("authz.lifecycle.detector-rate must be greater than zero");
        }
    }
}
