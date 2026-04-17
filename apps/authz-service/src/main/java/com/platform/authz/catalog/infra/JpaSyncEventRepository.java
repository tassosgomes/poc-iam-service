package com.platform.authz.catalog.infra;

import com.platform.authz.catalog.domain.SyncEvent;
import com.platform.authz.catalog.domain.SyncEventRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaSyncEventRepository implements SyncEventRepository {

    private final SpringDataSyncEventRepository springDataSyncEventRepository;

    public JpaSyncEventRepository(SpringDataSyncEventRepository springDataSyncEventRepository) {
        this.springDataSyncEventRepository = Objects.requireNonNull(
                springDataSyncEventRepository,
                "springDataSyncEventRepository must not be null"
        );
    }

    @Override
    public SyncEvent save(SyncEvent syncEvent) {
        SyncEventJpaEntity saved = springDataSyncEventRepository.save(toEntity(syncEvent));
        return toDomain(saved);
    }

    @Override
    public Optional<SyncEvent> findLatestByModuleId(UUID moduleId) {
        return springDataSyncEventRepository.findFirstByModuleIdOrderByOccurredAtDesc(moduleId)
                .map(this::toDomain);
    }

    private SyncEventJpaEntity toEntity(SyncEvent syncEvent) {
        SyncEventJpaEntity entity = new SyncEventJpaEntity();
        entity.setId(syncEvent.id());
        entity.setModuleId(syncEvent.moduleId());
        entity.setPayloadHash(syncEvent.payloadHash());
        entity.setSchemaVersion(syncEvent.schemaVersion());
        entity.setPermissionCount(syncEvent.permissionCount());
        entity.setAdded(syncEvent.added());
        entity.setUpdated(syncEvent.updated());
        entity.setDeprecated(syncEvent.deprecated());
        entity.setCatalogVersion(syncEvent.catalogVersion());
        entity.setOccurredAt(syncEvent.occurredAt());
        return entity;
    }

    private SyncEvent toDomain(SyncEventJpaEntity entity) {
        return new SyncEvent(
                entity.getId(),
                entity.getModuleId(),
                entity.getPayloadHash(),
                entity.getSchemaVersion(),
                entity.getPermissionCount(),
                entity.getAdded(),
                entity.getUpdated(),
                entity.getDeprecated(),
                entity.getCatalogVersion(),
                entity.getOccurredAt()
        );
    }
}
