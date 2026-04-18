package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaModuleRepository implements ModuleRepository {
    private final SpringDataModuleRepository springDataModuleRepository;
    private final ModuleEntityMapper moduleEntityMapper;

    public JpaModuleRepository(
            SpringDataModuleRepository springDataModuleRepository,
            ModuleEntityMapper moduleEntityMapper
    ) {
        this.springDataModuleRepository = Objects.requireNonNull(
                springDataModuleRepository,
                "springDataModuleRepository must not be null"
        );
        this.moduleEntityMapper = Objects.requireNonNull(moduleEntityMapper, "moduleEntityMapper must not be null");
    }

    @Override
    public Module save(Module module) {
        ModuleEntity savedEntity = springDataModuleRepository.save(moduleEntityMapper.toEntity(module));
        return moduleEntityMapper.toDomain(savedEntity);
    }

    @Override
    public boolean existsByAllowedPrefix(String allowedPrefix) {
        return springDataModuleRepository.existsByAllowedPrefix(allowedPrefix);
    }

    @Override
    public boolean existsByName(String name) {
        return springDataModuleRepository.existsByName(name);
    }

    @Override
    public Optional<Module> findById(UUID moduleId) {
        return springDataModuleRepository.findById(moduleId).map(moduleEntityMapper::toDomain);
    }

    @Override
    public Optional<Module> findByIdForUpdate(UUID moduleId) {
        return springDataModuleRepository.findByIdForUpdate(moduleId).map(moduleEntityMapper::toDomain);
    }

    @Override
    public void updateLastHeartbeatAt(UUID moduleId, Instant lastHeartbeatAt) {
        springDataModuleRepository.updateLastHeartbeatAt(moduleId, lastHeartbeatAt);
    }

    @Override
    public List<Module> findByLastHeartbeatAtBefore(Instant threshold) {
        return springDataModuleRepository.findByLastHeartbeatAtBeforeOrderByNameAsc(threshold).stream()
                .map(moduleEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Module> findAll() {
        return springDataModuleRepository.findAllByOrderByNameAsc().stream()
                .map(moduleEntityMapper::toDomain)
                .toList();
    }
}
