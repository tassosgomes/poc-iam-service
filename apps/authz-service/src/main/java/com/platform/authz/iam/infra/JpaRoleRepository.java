package com.platform.authz.iam.infra;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RolePage;
import com.platform.authz.iam.domain.RoleRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRoleRepository implements RoleRepository {

    private final SpringDataRoleRepository springDataRoleRepository;
    private final RoleEntityMapper roleEntityMapper;

    public JpaRoleRepository(
            SpringDataRoleRepository springDataRoleRepository,
            RoleEntityMapper roleEntityMapper
    ) {
        this.springDataRoleRepository = Objects.requireNonNull(
                springDataRoleRepository,
                "springDataRoleRepository must not be null"
        );
        this.roleEntityMapper = Objects.requireNonNull(roleEntityMapper, "roleEntityMapper must not be null");
    }

    @Override
    public Role save(Role role) {
        RoleJpaEntity savedEntity = springDataRoleRepository.saveAndFlush(roleEntityMapper.toEntity(role));
        return roleEntityMapper.toDomain(savedEntity);
    }

    @Override
    public java.util.Optional<Role> findById(UUID roleId) {
        return springDataRoleRepository.findById(roleId).map(roleEntityMapper::toDomain);
    }

    @Override
    public boolean existsByModuleIdAndName(UUID moduleId, String name) {
        return springDataRoleRepository.existsByModuleIdAndName(moduleId, name);
    }

    @Override
    public RolePage findPage(UUID moduleId, String query, int page, int size) {
        var rolePage = springDataRoleRepository.search(
                moduleId,
                query,
                PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "name"))
        );

        return new RolePage(
                rolePage.getContent().stream().map(roleEntityMapper::toDomain).toList(),
                rolePage.getTotalElements()
        );
    }

    @Override
    public void deleteById(UUID roleId) {
        springDataRoleRepository.deleteById(roleId);
    }
}
