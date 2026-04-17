package com.platform.authz.iam.infra;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cyberark")
public record CyberArkProperties(
        @NotBlank String issuerUri,
        @NotBlank String userApiBaseUrl
) {
}
