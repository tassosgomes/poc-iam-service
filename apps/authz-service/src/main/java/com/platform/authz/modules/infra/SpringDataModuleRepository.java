package com.platform.authz.modules.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataModuleRepository extends JpaRepository<ModuleEntity, UUID> {

    boolean existsByAllowedPrefix(String allowedPrefix);

    boolean existsByName(String name);

    List<ModuleEntity> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ModuleEntity m WHERE m.id = :id")
    Optional<ModuleEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE ModuleEntity m SET m.lastHeartbeatAt = :lastHeartbeatAt WHERE m.id = :id")
    void updateLastHeartbeatAt(@Param("id") UUID id, @Param("lastHeartbeatAt") Instant lastHeartbeatAt);
}
