package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;

public class UserSearchAccessDeniedException extends DomainException {

    public UserSearchAccessDeniedException() {
        super("User search requires PLATFORM_ADMIN or <MODULE>_USER_MANAGER authority");
    }
}
