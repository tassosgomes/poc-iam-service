package com.platform.authz.sdk;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the AuthZ SDK.
 *
 * <p>Prefix: {@code authz}
 */
@ConfigurationProperties(prefix = "authz")
public class AuthzProperties {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Whether the AuthZ SDK is enabled.
     */
    private boolean enabled = true;

    /**
     * Base URL of the AuthZ service (e.g. {@code http://authz-service:8081}).
     */
    private String baseUrl;

    /**
     * Module secret used only for {@code POST /v1/catalog/sync}.
     * <strong>Never logged.</strong>
     */
    private String moduleKey;

    /**
     * Module identifier registered in the AuthZ service.
     */
    private String moduleId;

    /**
     * HTTP response timeout. Defaults to 2 seconds.
     */
    private Duration timeout = DEFAULT_TIMEOUT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
