package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.UUID;

public class RoleDeletionConflictException extends DomainException {

    public RoleDeletionConflictException(UUID roleId) {
        super("Role '%s' cannot be deleted while it has active user assignments".formatted(roleId));
    }
}
