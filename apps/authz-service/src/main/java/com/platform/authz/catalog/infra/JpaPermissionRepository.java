package com.platform.authz.catalog.infra;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPermissionRepository implements PermissionRepository {

    private final SpringDataPermissionRepository springDataPermissionRepository;

    public JpaPermissionRepository(SpringDataPermissionRepository springDataPermissionRepository) {
        this.springDataPermissionRepository = Objects.requireNonNull(
                springDataPermissionRepository,
                "springDataPermissionRepository must not be null"
        );
    }

    @Override
    public Permission save(Permission permission) {
        PermissionJpaEntity saved = springDataPermissionRepository.save(toEntity(permission));
        return toDomain(saved);
    }

    @Override
    public List<Permission> saveAll(List<Permission> permissions) {
        List<PermissionJpaEntity> entities = permissions.stream().map(this::toEntity).toList();
        return springDataPermissionRepository.saveAll(entities).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Permission> findByModuleIdAndStatusIn(UUID moduleId, List<PermissionStatus> statuses) {
        return springDataPermissionRepository.findByModuleIdAndStatusIn(moduleId, statuses).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Permission> findByStatusAndSunsetAtBefore(PermissionStatus status, Instant sunsetAt) {
        return springDataPermissionRepository.findByStatusAndSunsetAtBefore(status, sunsetAt).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByStatus(PermissionStatus status) {
        return springDataPermissionRepository.countByStatus(status);
    }

    @Override
    public List<Permission> findByIds(Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }

        return springDataPermissionRepository.findAllById(permissionIds).stream()
                .map(this::toDomain)
                .toList();
    }

    private PermissionJpaEntity toEntity(Permission permission) {
        PermissionJpaEntity entity = new PermissionJpaEntity();
        entity.setId(permission.id());
        entity.setModuleId(permission.moduleId());
        entity.setCode(permission.code());
        entity.setDescription(permission.description());
        entity.setStatus(permission.status());
        entity.setSunsetAt(permission.sunsetAt());
        entity.setCreatedAt(permission.createdAt());
        entity.setUpdatedAt(permission.updatedAt());
        return entity;
    }

    private Permission toDomain(PermissionJpaEntity entity) {
        return new Permission(
                entity.getId(),
                entity.getModuleId(),
                entity.getCode(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getSunsetAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
