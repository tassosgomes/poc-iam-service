package com.platform.authz.catalog.domain;

import java.util.Optional;
import java.util.UUID;

public interface SyncEventRepository {

    SyncEvent save(SyncEvent syncEvent);

    Optional<SyncEvent> findLatestByModuleId(UUID moduleId);
}
