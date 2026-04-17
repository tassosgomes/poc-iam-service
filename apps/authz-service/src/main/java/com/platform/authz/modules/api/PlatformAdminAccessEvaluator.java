package com.platform.authz.modules.api;

import java.util.Objects;
import org.springframework.security.core.Authentication;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminAccessEvaluator {
    private final ModuleScopeExtractor moduleScopeExtractor;

    public PlatformAdminAccessEvaluator(ModuleScopeExtractor moduleScopeExtractor) {
        this.moduleScopeExtractor = Objects.requireNonNull(moduleScopeExtractor, "moduleScopeExtractor must not be null");
    }

    public void assertPlatformAdmin(Authentication authentication) {
        if (!moduleScopeExtractor.isPlatformAdmin(authentication)) {
            throw new PlatformAdminRequiredException();
        }
    }
}
