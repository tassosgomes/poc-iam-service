package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.RoleDeletionConflictException;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.iam.domain.UserRoleRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteRoleHandler {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public DeleteRoleHandler(
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
    }

    @Transactional
    public void handle(UUID roleId) {
        Objects.requireNonNull(roleId, "roleId must not be null");

        roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        if (userRoleRepository.existsActiveByRoleId(roleId)) {
            throw new RoleDeletionConflictException(roleId);
        }

        roleRepository.deleteById(roleId);
    }
}
