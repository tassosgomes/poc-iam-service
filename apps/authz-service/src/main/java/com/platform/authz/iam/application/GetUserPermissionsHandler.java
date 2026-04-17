package com.platform.authz.iam.application;

import com.platform.authz.config.CacheConfig;
import com.platform.authz.config.AuthzCacheProperties;
import com.platform.authz.iam.domain.UserRoleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserPermissionsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserPermissionsHandler.class);

    private final UserRoleRepository userRoleRepository;
    private final Clock clock;
    private final Duration ttl;

    public GetUserPermissionsHandler(
            UserRoleRepository userRoleRepository,
            Clock clock,
            AuthzCacheProperties cacheProperties
    ) {
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Objects.requireNonNull(cacheProperties, "cacheProperties must not be null").userPermissionsTtl();
    }

    @Cacheable(value = CacheConfig.USER_PERMISSIONS_CACHE, key = "#query.userId()")
    @Transactional(readOnly = true)
    public UserPermissions handle(GetUserPermissionsQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        LOGGER.debug("bulk_fetch_permissions userId={}", query.userId());

        List<String> permissionCodes = userRoleRepository.findDistinctPermissionCodesByUserId(query.userId());
        Instant resolvedAt = Instant.now(clock);

        return new UserPermissions(
                query.userId(),
                new LinkedHashSet<>(permissionCodes),
                resolvedAt,
                ttl
        );
    }
}
