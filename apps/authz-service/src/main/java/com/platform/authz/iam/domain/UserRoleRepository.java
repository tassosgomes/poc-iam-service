package com.platform.authz.iam.domain;

import java.util.UUID;

public interface UserRoleRepository {

    boolean existsActiveByRoleId(UUID roleId);
}
