package com.platform.authz.catalog.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

public interface PermissionRepository {

    Permission save(Permission permission);

    List<Permission> saveAll(List<Permission> permissions);

    List<Permission> findByModuleIdAndStatusIn(UUID moduleId, List<PermissionStatus> statuses);

    List<Permission> findByStatusAndSunsetAtBefore(PermissionStatus status, Instant sunsetAt);

    long countByStatus(PermissionStatus status);

    List<Permission> findByIds(Set<UUID> permissionIds);
}
