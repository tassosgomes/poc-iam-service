package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException(String userId) {
        super("User '%s' was not found".formatted(userId));
    }
}
