package com.platform.authz.iam.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository {

    boolean existsActiveByRoleId(UUID roleId);

    boolean existsActiveByUserIdAndRoleId(String userId, UUID roleId);

    Optional<UserRoleAssignment> findActiveByUserIdAndRoleId(String userId, UUID roleId);

    List<UserRoleAssignment> findActiveByUserId(String userId);

    List<String> findDistinctPermissionCodesByUserId(String userId);

    Optional<String> findPermissionStatusByUserIdAndCode(String userId, String permissionCode);

    UserRoleAssignment save(UserRoleAssignment userRoleAssignment);
}
