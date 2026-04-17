package com.platform.authz.shared.security;

import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public final class ModuleAuthenticationToken extends AbstractAuthenticationToken {
    private final ModuleContext principal;

    public ModuleAuthenticationToken(ModuleContext principal) {
        super(List.of());
        this.principal = Objects.requireNonNull(principal, "principal must not be null");
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public ModuleContext getPrincipal() {
        return principal;
    }
}
