package com.platform.authz.iam.domain;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findById(UUID roleId);

    boolean existsByModuleIdAndName(UUID moduleId, String name);

    RolePage findPage(UUID moduleId, String query, int page, int size);

    void deleteById(UUID roleId);
}
