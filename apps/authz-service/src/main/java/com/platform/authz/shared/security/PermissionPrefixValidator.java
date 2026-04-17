package com.platform.authz.shared.security;

import com.platform.authz.shared.exception.PrefixViolationException;
import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PermissionPrefixValidator {

    public void validate(String permissionCode, String allowedPrefix) {
        Objects.requireNonNull(permissionCode, "permissionCode must not be null");
        Objects.requireNonNull(allowedPrefix, "allowedPrefix must not be null");

        String expectedPrefix = allowedPrefix + ".";
        if (!permissionCode.startsWith(expectedPrefix)) {
            throw new PrefixViolationException(permissionCode, allowedPrefix);
        }
    }

    public void validateAll(Collection<String> permissionCodes, String allowedPrefix) {
        Objects.requireNonNull(permissionCodes, "permissionCodes must not be null");

        permissionCodes.forEach(permissionCode -> validate(permissionCode, allowedPrefix));
    }
}
