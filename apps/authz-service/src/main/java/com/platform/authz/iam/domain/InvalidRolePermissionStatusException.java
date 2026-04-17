package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.UUID;

public class InvalidRolePermissionStatusException extends DomainException {

    public InvalidRolePermissionStatusException(UUID permissionId, String status) {
        super("Permission '%s' with status '%s' cannot be assigned to a role".formatted(permissionId, status));
    }
}
