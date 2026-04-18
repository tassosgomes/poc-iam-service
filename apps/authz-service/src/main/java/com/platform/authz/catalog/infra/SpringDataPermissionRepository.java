package com.platform.authz.catalog.infra;

import com.platform.authz.catalog.domain.PermissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPermissionRepository extends JpaRepository<PermissionJpaEntity, UUID> {

    long countByModuleId(UUID moduleId);

    List<PermissionJpaEntity> findByModuleIdAndStatusIn(UUID moduleId, List<PermissionStatus> statuses);

    List<PermissionJpaEntity> findByStatusAndSunsetAtBefore(PermissionStatus status, Instant sunsetAt);

    long countByStatus(PermissionStatus status);
}
