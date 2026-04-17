package com.platform.authz.shared.exception;

import com.platform.authz.shared.domain.DomainException;

public class ModuleIdMismatchException extends DomainException {

    public ModuleIdMismatchException(String bodyModuleId, String authenticatedModuleId) {
        super("Request moduleId '%s' does not match authenticated module '%s'"
                .formatted(bodyModuleId, authenticatedModuleId));
    }
}
