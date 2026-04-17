package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRoleHandler {

    private final RoleRepository roleRepository;
    private final RoleViewProjector roleViewProjector;

    public GetRoleHandler(RoleRepository roleRepository, RoleViewProjector roleViewProjector) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleViewProjector = Objects.requireNonNull(roleViewProjector, "roleViewProjector must not be null");
    }

    @Transactional(readOnly = true)
    public RoleView handle(UUID roleId) {
        Objects.requireNonNull(roleId, "roleId must not be null");

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        return roleViewProjector.project(role);
    }
}
