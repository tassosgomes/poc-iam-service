package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleKeyStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaModuleKeyRepository implements ModuleKeyRepository {
    private final SpringDataModuleKeyRepository springDataModuleKeyRepository;
    private final ModuleKeyEntityMapper moduleKeyEntityMapper;

    public JpaModuleKeyRepository(
            SpringDataModuleKeyRepository springDataModuleKeyRepository,
            ModuleKeyEntityMapper moduleKeyEntityMapper
    ) {
        this.springDataModuleKeyRepository = Objects.requireNonNull(
                springDataModuleKeyRepository,
                "springDataModuleKeyRepository must not be null"
        );
        this.moduleKeyEntityMapper = Objects.requireNonNull(
                moduleKeyEntityMapper,
                "moduleKeyEntityMapper must not be null"
        );
    }

    @Override
    public ModuleKey save(ModuleKey moduleKey) {
        ModuleKeyEntity savedEntity = springDataModuleKeyRepository.save(moduleKeyEntityMapper.toEntity(moduleKey));
        return moduleKeyEntityMapper.toDomain(savedEntity);
    }

    @Override
    public ModuleKey saveAndFlush(ModuleKey moduleKey) {
        ModuleKeyEntity savedEntity = springDataModuleKeyRepository.saveAndFlush(moduleKeyEntityMapper.toEntity(moduleKey));
        return moduleKeyEntityMapper.toDomain(savedEntity);
    }

    @Override
    public List<ModuleKey> saveAll(Collection<ModuleKey> moduleKeys) {
        return springDataModuleKeyRepository.saveAll(
                        moduleKeys.stream().map(moduleKeyEntityMapper::toEntity).toList()
                ).stream()
                .map(moduleKeyEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<ModuleKey> findActiveByModuleId(UUID moduleId) {
        return springDataModuleKeyRepository.findByModuleIdAndStatusOrderByCreatedAtDesc(
                        moduleId,
                        ModuleKeyStatus.ACTIVE
                ).stream()
                .findFirst()
                .map(moduleKeyEntityMapper::toDomain);
    }

    @Override
    public Map<UUID, ModuleKey> findActiveByModuleIds(Collection<UUID> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return Map.of();
        }

        return springDataModuleKeyRepository.findByModuleIdInAndStatusOrderByModuleIdAscCreatedAtDesc(
                        moduleIds,
                        ModuleKeyStatus.ACTIVE
                ).stream()
                .map(moduleKeyEntityMapper::toDomain)
                .collect(Collectors.toMap(
                        ModuleKey::moduleId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    @Override
    public Optional<ModuleKey> findActiveByModuleIdForUpdate(UUID moduleId) {
        return springDataModuleKeyRepository.findByModuleIdAndStatusOrderByCreatedAtDescForUpdate(
                        moduleId,
                        ModuleKeyStatus.ACTIVE
                ).stream()
                .findFirst()
                .map(moduleKeyEntityMapper::toDomain);
    }

    @Override
    public List<ModuleKey> findActiveOrInGraceByModuleId(UUID moduleId, Instant referenceTime) {
        return springDataModuleKeyRepository.findActiveOrInGraceByModuleId(moduleId, referenceTime).stream()
                .map(moduleKeyEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<ModuleKey> findByModuleId(UUID moduleId) {
        return springDataModuleKeyRepository.findByModuleIdOrderByCreatedAtDesc(moduleId).stream()
                .map(moduleKeyEntityMapper::toDomain)
                .toList();
    }
}
