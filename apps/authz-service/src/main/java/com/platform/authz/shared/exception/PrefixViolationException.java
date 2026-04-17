package com.platform.authz.shared.exception;

import com.platform.authz.shared.domain.DomainException;

public class PrefixViolationException extends DomainException {

    public PrefixViolationException(String permissionCode, String allowedPrefix) {
        super("Permission '%s' is outside the allowed prefix '%s'".formatted(permissionCode, allowedPrefix));
    }
}
