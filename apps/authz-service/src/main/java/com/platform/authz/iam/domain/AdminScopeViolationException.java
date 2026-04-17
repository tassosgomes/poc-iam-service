package com.platform.authz.iam.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.Objects;
import java.util.UUID;

public class AdminScopeViolationException extends DomainException {

    private final UUID moduleId;
    private final String moduleScope;

    public AdminScopeViolationException(UUID moduleId, String moduleScope) {
        super("The authenticated user cannot manage users for module '%s'".formatted(moduleScope));
        this.moduleId = Objects.requireNonNull(moduleId, "moduleId must not be null");
        this.moduleScope = Objects.requireNonNull(moduleScope, "moduleScope must not be null");
    }

    public UUID moduleId() {
        return moduleId;
    }

    public String moduleScope() {
        return moduleScope;
    }
}
