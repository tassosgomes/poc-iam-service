package com.platform.authz.iam.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataUserRoleRepository extends JpaRepository<UserRoleJpaEntity, UUID> {

    boolean existsByRoleIdAndRevokedAtIsNull(UUID roleId);

    boolean existsByUserIdAndRoleIdAndRevokedAtIsNull(String userId, UUID roleId);

    Optional<UserRoleJpaEntity> findByUserIdAndRoleIdAndRevokedAtIsNull(String userId, UUID roleId);

    List<UserRoleJpaEntity> findByUserIdAndRevokedAtIsNullOrderByAssignedAtAsc(String userId);
}
