package com.platform.authz.iam.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRoleRepository extends JpaRepository<UserRoleJpaEntity, UUID> {

    boolean existsByRoleIdAndRevokedAtIsNull(UUID roleId);

    boolean existsByUserIdAndRoleIdAndRevokedAtIsNull(String userId, UUID roleId);

    Optional<UserRoleJpaEntity> findByUserIdAndRoleIdAndRevokedAtIsNull(String userId, UUID roleId);

    List<UserRoleJpaEntity> findByUserIdAndRevokedAtIsNullOrderByAssignedAtAsc(String userId);

    @Query(nativeQuery = true, value = """
            SELECT DISTINCT p.code
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ur.user_id = :userId
              AND ur.revoked_at IS NULL
              AND p.status IN ('ACTIVE', 'DEPRECATED')
            ORDER BY p.code
            """)
    List<String> findDistinctPermissionCodesByUserId(@Param("userId") String userId);

    @Query(nativeQuery = true, value = """
            SELECT MIN(p.status)
            FROM user_role ur
            JOIN role r ON r.id = ur.role_id
            JOIN role_permission rp ON rp.role_id = r.id
            JOIN permission p ON p.id = rp.permission_id
            WHERE ur.user_id = :userId
              AND ur.revoked_at IS NULL
              AND p.code = :permissionCode
              AND p.status IN ('ACTIVE', 'DEPRECATED')
            """)
    Optional<String> findPermissionStatusByUserIdAndCode(
            @Param("userId") String userId,
            @Param("permissionCode") String permissionCode
    );
}
