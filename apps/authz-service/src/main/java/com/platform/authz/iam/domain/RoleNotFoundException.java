package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.UUID;

public class RoleNotFoundException extends DomainException {

    public RoleNotFoundException(UUID roleId) {
        super("Role '%s' was not found".formatted(roleId));
    }
}
