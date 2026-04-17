package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.UserSearchAccessDeniedException;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UserSearchService {

    private final UserSearchPort userSearchPort;
    private final ModuleScopeExtractor moduleScopeExtractor;

    public UserSearchService(
            UserSearchPort userSearchPort,
            ModuleScopeExtractor moduleScopeExtractor
    ) {
        this.userSearchPort = Objects.requireNonNull(
                userSearchPort, "userSearchPort must not be null"
        );
        this.moduleScopeExtractor = Objects.requireNonNull(
                moduleScopeExtractor, "moduleScopeExtractor must not be null"
        );
    }

    public List<UserSummary> search(Authentication authentication, String query, String requestedModule) {
        boolean isPlatformAdmin = moduleScopeExtractor.isPlatformAdmin(authentication);
        List<String> manageableModules = moduleScopeExtractor.extractManageableModules(authentication);

        if (!isPlatformAdmin && manageableModules.isEmpty()) {
            throw new UserSearchAccessDeniedException();
        }

        String normalizedRequestedModule = normalizeModule(requestedModule);
        if (!isPlatformAdmin && normalizedRequestedModule != null && !manageableModules.contains(normalizedRequestedModule)) {
            return List.of();
        }

        List<UserSummary> users = userSearchPort.searchUsers(query, normalizedRequestedModule);
        Set<String> allowedModules = resolveAllowedModules(isPlatformAdmin, manageableModules, normalizedRequestedModule);

        return users.stream()
                .map(user -> filterUserModules(user, allowedModules))
                .filter(Objects::nonNull)
                .toList();
    }

    private Set<String> resolveAllowedModules(
            boolean isPlatformAdmin,
            List<String> manageableModules,
            String requestedModule
    ) {
        if (requestedModule != null) {
            return Set.of(requestedModule);
        }

        if (isPlatformAdmin) {
            return Set.of();
        }

        return Set.copyOf(manageableModules);
    }

    private UserSummary filterUserModules(UserSummary user, Set<String> allowedModules) {
        List<String> userModules = user.modules() != null ? user.modules() : List.of();
        if (allowedModules.isEmpty()) {
            return new UserSummary(
                    user.userId(),
                    user.displayName(),
                    user.email(),
                    userModules.stream().map(this::normalizeModule).filter(Objects::nonNull).toList()
            );
        }

        List<String> filteredModules = userModules.stream()
                .map(this::normalizeModule)
                .filter(Objects::nonNull)
                .filter(allowedModules::contains)
                .toList();
        if (filteredModules.isEmpty()) {
            return null;
        }

        return new UserSummary(
                user.userId(),
                user.displayName(),
                user.email(),
                filteredModules
        );
    }

    private String normalizeModule(String module) {
        if (module == null || module.isBlank()) {
            return null;
        }

        return module.trim().toLowerCase();
    }
}
