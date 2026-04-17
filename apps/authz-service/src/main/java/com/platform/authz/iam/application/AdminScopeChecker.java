package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.AdminScopeViolationException;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AdminScopeChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminScopeChecker.class);

    private final ModuleRepository moduleRepository;
    private final ModuleScopeExtractor moduleScopeExtractor;

    public AdminScopeChecker(
            ModuleRepository moduleRepository,
            ModuleScopeExtractor moduleScopeExtractor
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.moduleScopeExtractor = Objects.requireNonNull(moduleScopeExtractor, "moduleScopeExtractor must not be null");
    }

    public boolean hasManagementAccess(Authentication authentication) {
        return moduleScopeExtractor.isPlatformAdmin(authentication)
                || !moduleScopeExtractor.extractManageableModules(authentication).isEmpty();
    }

    public void requireScope(Authentication authentication, UUID roleModuleId) {
        Objects.requireNonNull(roleModuleId, "roleModuleId must not be null");

        Module module = moduleRepository.findById(roleModuleId)
                .orElseThrow(() -> new ModuleNotFoundException(roleModuleId));

        if (moduleScopeExtractor.isPlatformAdmin(authentication)) {
            return;
        }

        if (moduleScopeExtractor.canManageModule(authentication, module.allowedPrefix())) {
            return;
        }

        LOGGER.warn(
                "admin_scope_violation actor={} module={} roleModuleId={}",
                resolveActor(authentication),
                module.allowedPrefix(),
                roleModuleId
        );
        throw new AdminScopeViolationException(roleModuleId, module.allowedPrefix());
    }

    private String resolveActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "unknown";
        }

        return authentication.getName();
    }
}
