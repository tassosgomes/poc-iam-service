package com.platform.authz.authz.application;

import com.platform.authz.iam.application.GetUserPermissionsHandler;
import com.platform.authz.iam.application.GetUserPermissionsQuery;
import com.platform.authz.iam.application.UserPermissions;
import com.platform.authz.iam.domain.UserRoleRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckPermissionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CheckPermissionHandler.class);
    private static final String STATUS_DEPRECATED = "DEPRECATED";

    private final GetUserPermissionsHandler getUserPermissionsHandler;
    private final UserRoleRepository userRoleRepository;

    public CheckPermissionHandler(
            GetUserPermissionsHandler getUserPermissionsHandler,
            UserRoleRepository userRoleRepository
    ) {
        this.getUserPermissionsHandler = Objects.requireNonNull(
                getUserPermissionsHandler,
                "getUserPermissionsHandler must not be null"
        );
        this.userRoleRepository = Objects.requireNonNull(
                userRoleRepository,
                "userRoleRepository must not be null"
        );
    }

    @Transactional(readOnly = true)
    public CheckPermissionResult handle(CheckPermissionQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        LOGGER.debug("check_permission userId={} permission={}", query.userId(), query.permission());

        UserPermissions permissions = getUserPermissionsHandler.handle(
                new GetUserPermissionsQuery(query.userId())
        );

        boolean allowed = permissions.permissions().contains(query.permission());
        if (!allowed) {
            return CheckPermissionResult.denied();
        }

        String source = resolveSource(query.userId(), query.permission());
        return new CheckPermissionResult(true, source);
    }

    private String resolveSource(String userId, String permissionCode) {
        return userRoleRepository.findPermissionStatusByUserIdAndCode(userId, permissionCode)
                .map(status -> STATUS_DEPRECATED.equals(status)
                        ? CheckPermissionResult.SOURCE_DEPRECATED
                        : CheckPermissionResult.SOURCE_ACTIVE)
                .orElse(CheckPermissionResult.SOURCE_ACTIVE);
    }
}
