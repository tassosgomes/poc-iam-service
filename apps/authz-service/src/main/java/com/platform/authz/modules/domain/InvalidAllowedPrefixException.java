package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.ValidationPatterns;
import com.platform.authz.shared.domain.DomainException;

public class InvalidAllowedPrefixException extends DomainException {

    public InvalidAllowedPrefixException(String allowedPrefix) {
        super("Allowed prefix must match " + ValidationPatterns.ALLOWED_PREFIX_REGEX);
    }
}
