package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.UUID;

public class PermissionModuleMismatchException extends DomainException {

    public PermissionModuleMismatchException(UUID moduleId) {
        super("All role permissions must belong to module '%s'".formatted(moduleId));
    }
}
