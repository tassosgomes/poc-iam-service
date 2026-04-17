package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;

public class UserRoleAssignmentConflictException extends DomainException {

    public UserRoleAssignmentConflictException(String userId, String roleName) {
        super("An active assignment for user '%s' and role '%s' already exists".formatted(userId, roleName));
    }
}
