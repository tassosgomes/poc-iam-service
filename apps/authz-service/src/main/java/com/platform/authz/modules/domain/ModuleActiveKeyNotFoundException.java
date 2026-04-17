package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.UUID;

public class ModuleActiveKeyNotFoundException extends DomainException {

    public ModuleActiveKeyNotFoundException(UUID moduleId) {
        super("Module '%s' does not have an active key".formatted(moduleId));
    }
}
