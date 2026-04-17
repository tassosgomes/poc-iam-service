package com.platform.authz.modules.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataModuleRepository extends JpaRepository<ModuleEntity, UUID> {

    boolean existsByAllowedPrefix(String allowedPrefix);

    boolean existsByName(String name);

    List<ModuleEntity> findAllByOrderByNameAsc();
}
