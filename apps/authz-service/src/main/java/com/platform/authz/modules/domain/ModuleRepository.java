package com.platform.authz.modules.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModuleRepository {

    Module save(Module module);

    boolean existsByAllowedPrefix(String allowedPrefix);

    boolean existsByName(String name);

    Optional<Module> findById(UUID moduleId);

    Optional<Module> findByIdForUpdate(UUID moduleId);

    void updateLastHeartbeatAt(UUID moduleId, Instant lastHeartbeatAt);

    List<Module> findAll();
}
