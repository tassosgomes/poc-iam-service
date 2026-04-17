package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.ModuleKeyStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpringDataModuleKeyRepository extends JpaRepository<ModuleKeyEntity, UUID> {

    List<ModuleKeyEntity> findByModuleIdAndStatusOrderByCreatedAtDesc(
            UUID moduleId,
            ModuleKeyStatus status
    );

    List<ModuleKeyEntity> findByModuleIdInAndStatusOrderByModuleIdAscCreatedAtDesc(
            Collection<UUID> moduleIds,
            ModuleKeyStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select moduleKey
            from ModuleKeyEntity moduleKey
            where moduleKey.moduleId = :moduleId
              and moduleKey.status = :status
            order by moduleKey.createdAt desc
            """)
    List<ModuleKeyEntity> findByModuleIdAndStatusOrderByCreatedAtDescForUpdate(
            UUID moduleId,
            ModuleKeyStatus status
    );

    List<ModuleKeyEntity> findByModuleIdOrderByCreatedAtDesc(UUID moduleId);
}
