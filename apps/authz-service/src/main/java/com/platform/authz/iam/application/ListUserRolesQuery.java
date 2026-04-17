package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.iam.domain.UserNotFoundException;
import com.platform.authz.iam.domain.UserRoleAssignment;
import com.platform.authz.iam.domain.UserRoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListUserRolesQuery {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final ModuleRepository moduleRepository;
    private final UserSearchPort userSearchPort;
    private final AdminScopeChecker adminScopeChecker;
    private final ModuleScopeExtractor moduleScopeExtractor;

    public ListUserRolesQuery(
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            ModuleRepository moduleRepository,
            UserSearchPort userSearchPort,
            AdminScopeChecker adminScopeChecker,
            ModuleScopeExtractor moduleScopeExtractor
    ) {
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.userSearchPort = Objects.requireNonNull(userSearchPort, "userSearchPort must not be null");
        this.adminScopeChecker = Objects.requireNonNull(adminScopeChecker, "adminScopeChecker must not be null");
        this.moduleScopeExtractor = Objects.requireNonNull(moduleScopeExtractor, "moduleScopeExtractor must not be null");
    }

    @Transactional(readOnly = true)
    public List<UserRoleView> handle(String userId, Authentication authentication) {
        Objects.requireNonNull(userId, "userId must not be null");

        if (!userSearchPort.userExists(userId)) {
            throw new UserNotFoundException(userId);
        }

        if (!adminScopeChecker.hasManagementAccess(authentication)) {
            throw new AccessDeniedException("The authenticated user cannot manage user roles");
        }

        List<UserRoleAssignment> assignments = userRoleRepository.findActiveByUserId(userId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        Map<java.util.UUID, Role> rolesById = roleRepository.findByIds(assignments.stream()
                        .map(UserRoleAssignment::roleId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Role::id, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        if (moduleScopeExtractor.isPlatformAdmin(authentication)) {
            return assignments.stream()
                    .map(assignment -> toView(assignment, rolesById))
                    .sorted(java.util.Comparator.comparing(UserRoleView::roleName))
                    .toList();
        }

        Set<String> manageableModules = Set.copyOf(moduleScopeExtractor.extractManageableModules(authentication));
        Map<java.util.UUID, String> moduleScopes = resolveModuleScopes(rolesById.values());

        return assignments.stream()
                .map(assignment -> toView(assignment, rolesById))
                .filter(view -> manageableModules.contains(moduleScopes.get(view.moduleId())))
                .sorted(java.util.Comparator.comparing(UserRoleView::roleName))
                .toList();
    }

    private Map<java.util.UUID, String> resolveModuleScopes(java.util.Collection<Role> roles) {
        return roles.stream()
                .map(Role::moduleId)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        moduleId -> moduleRepository.findById(moduleId)
                                .map(Module::allowedPrefix)
                                .map(moduleScopeExtractor::normalizeModuleScope)
                                .orElseThrow(() -> new ModuleNotFoundException(moduleId))
                ));
    }

    private UserRoleView toView(UserRoleAssignment assignment, Map<java.util.UUID, Role> rolesById) {
        Role role = rolesById.get(assignment.roleId());
        if (role == null) {
            throw new RoleNotFoundException(assignment.roleId());
        }

        return new UserRoleView(
                assignment.userId(),
                role.id(),
                role.moduleId(),
                role.name(),
                assignment.assignedBy(),
                assignment.assignedAt()
        );
    }
}
