package com.platform.authz.catalog.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSyncEventRepository extends JpaRepository<SyncEventJpaEntity, UUID> {

    long countByModuleId(UUID moduleId);

    Optional<SyncEventJpaEntity> findFirstByModuleIdOrderByOccurredAtDesc(UUID moduleId);
}
