package com.platform.authz.modules.domain;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ModuleKeyRepository {

    ModuleKey save(ModuleKey moduleKey);

    ModuleKey saveAndFlush(ModuleKey moduleKey);

    List<ModuleKey> saveAll(Collection<ModuleKey> moduleKeys);

    Optional<ModuleKey> findActiveByModuleId(UUID moduleId);

    Map<UUID, ModuleKey> findActiveByModuleIds(Collection<UUID> moduleIds);

    Optional<ModuleKey> findActiveByModuleIdForUpdate(UUID moduleId);

    List<ModuleKey> findActiveOrInGraceByModuleId(UUID moduleId, Instant referenceTime);

    List<ModuleKey> findByModuleId(UUID moduleId);
}
