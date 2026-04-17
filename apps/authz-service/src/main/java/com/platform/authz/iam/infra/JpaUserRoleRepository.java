package com.platform.authz.iam.infra;

import com.platform.authz.iam.domain.UserRoleRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRoleRepository implements UserRoleRepository {

    private final SpringDataUserRoleRepository springDataUserRoleRepository;

    public JpaUserRoleRepository(SpringDataUserRoleRepository springDataUserRoleRepository) {
        this.springDataUserRoleRepository = Objects.requireNonNull(
                springDataUserRoleRepository,
                "springDataUserRoleRepository must not be null"
        );
    }

    @Override
    public boolean existsActiveByRoleId(UUID roleId) {
        return springDataUserRoleRepository.existsByRoleIdAndRevokedAtIsNull(roleId);
    }
}
