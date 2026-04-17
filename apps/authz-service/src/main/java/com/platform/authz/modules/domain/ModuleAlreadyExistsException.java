package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.DomainException;

public class ModuleAlreadyExistsException extends DomainException {

    public ModuleAlreadyExistsException(String fieldName, String fieldValue) {
        super("Module with %s '%s' already exists".formatted(fieldName, fieldValue));
    }
}
