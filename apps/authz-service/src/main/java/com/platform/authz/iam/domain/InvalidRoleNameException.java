package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;

public class InvalidRoleNameException extends DomainException {

    public InvalidRoleNameException(String roleName) {
        super("Role name '%s' must match ^[A-Z]+(?:_[A-Z0-9]+)+$".formatted(roleName));
    }
}
