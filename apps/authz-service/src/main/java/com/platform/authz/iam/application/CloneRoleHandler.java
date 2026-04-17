package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleConflictException;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.Module;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloneRoleHandler {

    private final RoleRepository roleRepository;
    private final RoleManagementSupport roleManagementSupport;
    private final Clock clock;

    public CloneRoleHandler(
            RoleRepository roleRepository,
            RoleManagementSupport roleManagementSupport,
            Clock clock
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleManagementSupport = Objects.requireNonNull(roleManagementSupport, "roleManagementSupport must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public Role handle(CloneRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Role sourceRole = roleRepository.findById(command.roleId())
                .orElseThrow(() -> new RoleNotFoundException(command.roleId()));
        Module module = roleManagementSupport.getModule(sourceRole.moduleId());

        String cloneName = roleManagementSupport.generateCloneName(
                module,
                sourceRole.name(),
                command.name(),
                candidateName -> roleRepository.existsByModuleIdAndName(sourceRole.moduleId(), candidateName)
        );
        roleManagementSupport.validateRoleName(module, cloneName);

        if (roleRepository.existsByModuleIdAndName(sourceRole.moduleId(), cloneName)) {
            throw new RoleConflictException(cloneName);
        }

        Role clonedRole = sourceRole.cloneAs(cloneName, command.createdBy(), Instant.now(clock));
        try {
            return roleRepository.save(clonedRole);
        } catch (DataIntegrityViolationException exception) {
            throw new RoleConflictException(cloneName);
        }
    }
}
