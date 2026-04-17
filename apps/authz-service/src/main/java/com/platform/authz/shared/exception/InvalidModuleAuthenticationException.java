package com.platform.authz.shared.exception;

import com.platform.authz.shared.domain.DomainException;

public class InvalidModuleAuthenticationException extends DomainException {

    public InvalidModuleAuthenticationException(String actualType) {
        super("Expected module authentication but got: %s".formatted(actualType));
    }
}
