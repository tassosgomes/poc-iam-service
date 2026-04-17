package com.platform.authz.iam.infra;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRoleRepository extends JpaRepository<UserRoleJpaEntity, UUID> {

    boolean existsByRoleIdAndRevokedAtIsNull(UUID roleId);
}
