package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.DomainException;

public class ModuleConflictException extends DomainException {

    public ModuleConflictException() {
        super("Module name or allowedPrefix already exists");
    }
}
