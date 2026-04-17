package com.platform.authz.iam.api;

import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class RoleAccessEvaluator {

    private final ModuleRepository moduleRepository;
    private final RoleRepository roleRepository;
    private final ModuleScopeExtractor moduleScopeExtractor;

    public RoleAccessEvaluator(
            ModuleRepository moduleRepository,
            RoleRepository roleRepository,
            ModuleScopeExtractor moduleScopeExtractor
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.moduleScopeExtractor = Objects.requireNonNull(moduleScopeExtractor, "moduleScopeExtractor must not be null");
    }

    public void assertCanManageModule(Authentication authentication, UUID moduleId) {
        String allowedPrefix = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException(moduleId))
                .allowedPrefix();

        if (!moduleScopeExtractor.canManageModule(authentication, allowedPrefix)) {
            throw new AccessDeniedException("The authenticated user cannot manage roles for the selected module");
        }
    }

    public void assertCanManageRole(Authentication authentication, UUID roleId) {
        UUID moduleId = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId))
                .moduleId();
        assertCanManageModule(authentication, moduleId);
    }
}
