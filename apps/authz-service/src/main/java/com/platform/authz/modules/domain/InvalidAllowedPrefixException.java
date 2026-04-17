package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.DomainException;

public class InvalidAllowedPrefixException extends DomainException {

    public InvalidAllowedPrefixException(String allowedPrefix) {
        super("Allowed prefix must match ^[a-z][a-z0-9-]{1,30}$");
    }
}
