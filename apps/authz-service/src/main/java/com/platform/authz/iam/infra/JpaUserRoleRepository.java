package com.platform.authz.iam.infra;

import com.platform.authz.iam.domain.UserRoleAssignment;
import com.platform.authz.iam.domain.UserRoleRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRoleRepository implements UserRoleRepository {

    private final SpringDataUserRoleRepository springDataUserRoleRepository;
    private final UserRoleEntityMapper userRoleEntityMapper;

    public JpaUserRoleRepository(
            SpringDataUserRoleRepository springDataUserRoleRepository,
            UserRoleEntityMapper userRoleEntityMapper
    ) {
        this.springDataUserRoleRepository = Objects.requireNonNull(
                springDataUserRoleRepository,
                "springDataUserRoleRepository must not be null"
        );
        this.userRoleEntityMapper = Objects.requireNonNull(userRoleEntityMapper, "userRoleEntityMapper must not be null");
    }

    @Override
    public boolean existsActiveByRoleId(UUID roleId) {
        return springDataUserRoleRepository.existsByRoleIdAndRevokedAtIsNull(roleId);
    }

    @Override
    public boolean existsActiveByUserIdAndRoleId(String userId, UUID roleId) {
        return springDataUserRoleRepository.existsByUserIdAndRoleIdAndRevokedAtIsNull(userId, roleId);
    }

    @Override
    public Optional<UserRoleAssignment> findActiveByUserIdAndRoleId(String userId, UUID roleId) {
        return springDataUserRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(userId, roleId)
                .map(userRoleEntityMapper::toDomain);
    }

    @Override
    public List<UserRoleAssignment> findActiveByUserId(String userId) {
        return springDataUserRoleRepository.findByUserIdAndRevokedAtIsNullOrderByAssignedAtAsc(userId).stream()
                .map(userRoleEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<String> findDistinctPermissionCodesByUserId(String userId) {
        return springDataUserRoleRepository.findDistinctPermissionCodesByUserId(userId);
    }

    @Override
    public UserRoleAssignment save(UserRoleAssignment userRoleAssignment) {
        UserRoleJpaEntity savedEntity = springDataUserRoleRepository.saveAndFlush(
                userRoleEntityMapper.toEntity(userRoleAssignment)
        );
        return userRoleEntityMapper.toDomain(savedEntity);
    }
}
