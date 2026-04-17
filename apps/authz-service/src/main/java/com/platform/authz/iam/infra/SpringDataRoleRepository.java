package com.platform.authz.iam.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataRoleRepository extends JpaRepository<RoleJpaEntity, UUID> {

    boolean existsByModuleIdAndName(UUID moduleId, String name);

    @Query("""
            SELECT r
            FROM RoleJpaEntity r
            WHERE r.moduleId = :moduleId
              AND (
                :query IS NULL
                OR trim(:query) = ''
                OR lower(r.name) LIKE lower(concat('%', :query, '%'))
                OR lower(r.description) LIKE lower(concat('%', :query, '%'))
              )
            """)
    Page<RoleJpaEntity> search(@Param("moduleId") UUID moduleId, @Param("query") String query, Pageable pageable);

    @Override
    Optional<RoleJpaEntity> findById(UUID roleId);
}
