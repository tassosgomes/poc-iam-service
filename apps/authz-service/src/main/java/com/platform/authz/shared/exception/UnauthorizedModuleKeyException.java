package com.platform.authz.shared.exception;

import com.platform.authz.shared.domain.DomainException;
import java.util.Objects;

public class UnauthorizedModuleKeyException extends DomainException {
    private final String reason;

    public UnauthorizedModuleKeyException(String reason) {
        super("Module key invalid or expired");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public String reason() {
        return reason;
    }
}
