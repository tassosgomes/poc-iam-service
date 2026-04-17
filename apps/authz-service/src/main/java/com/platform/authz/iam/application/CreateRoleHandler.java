package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleConflictException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.Module;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRoleHandler {

    private final RoleRepository roleRepository;
    private final RoleManagementSupport roleManagementSupport;
    private final Clock clock;

    public CreateRoleHandler(
            RoleRepository roleRepository,
            RoleManagementSupport roleManagementSupport,
            Clock clock
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleManagementSupport = Objects.requireNonNull(roleManagementSupport, "roleManagementSupport must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public Role handle(CreateRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Module module = roleManagementSupport.getModule(command.moduleId());
        String normalizedName = command.name().trim();
        roleManagementSupport.validateRoleName(module, normalizedName);
        roleManagementSupport.validatePermissions(command.moduleId(), command.permissionIds());

        if (roleRepository.existsByModuleIdAndName(command.moduleId(), normalizedName)) {
            throw new RoleConflictException(normalizedName);
        }

        Role role = Role.create(
                command.moduleId(),
                normalizedName,
                command.description(),
                command.createdBy(),
                Instant.now(clock),
                new LinkedHashSet<>(command.permissionIds())
        );

        try {
            return roleRepository.save(role);
        } catch (DataIntegrityViolationException exception) {
            throw new RoleConflictException(normalizedName);
        }
    }
}
