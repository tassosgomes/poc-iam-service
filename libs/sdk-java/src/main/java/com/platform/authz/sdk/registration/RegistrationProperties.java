package com.platform.authz.sdk.registration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for SDK self-registration.
 *
 * <p>Prefix: {@code authz.registration}
 */
@ConfigurationProperties(prefix = "authz.registration")
public class RegistrationProperties {

    private boolean enabled = true;
    private String permissionsFile = "permissions.yaml";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPermissionsFile() {
        return permissionsFile;
    }

    public void setPermissionsFile(String permissionsFile) {
        this.permissionsFile = permissionsFile;
    }
}
