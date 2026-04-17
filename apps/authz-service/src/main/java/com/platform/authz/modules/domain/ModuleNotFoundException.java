package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.DomainException;
import java.util.UUID;

public class ModuleNotFoundException extends DomainException {

    public ModuleNotFoundException(UUID moduleId) {
        super("Module '%s' was not found".formatted(moduleId));
    }
}
