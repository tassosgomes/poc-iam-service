package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;

public class RoleConflictException extends DomainException {

    public RoleConflictException(String name) {
        super("Role '%s' already exists for the selected module".formatted(name));
    }
}
