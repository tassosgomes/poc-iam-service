package com.platform.authz.catalog.domain;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PermissionRepository {

    Permission save(Permission permission);

    List<Permission> saveAll(List<Permission> permissions);

    List<Permission> findByModuleIdAndStatusIn(UUID moduleId, List<PermissionStatus> statuses);

    List<Permission> findByIds(Set<UUID> permissionIds);
}
