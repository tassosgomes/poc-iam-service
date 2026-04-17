package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleConflictException;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.modules.domain.Module;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateRoleHandler {

    private final RoleRepository roleRepository;
    private final RoleManagementSupport roleManagementSupport;

    public UpdateRoleHandler(
            RoleRepository roleRepository,
            RoleManagementSupport roleManagementSupport
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleManagementSupport = Objects.requireNonNull(roleManagementSupport, "roleManagementSupport must not be null");
    }

    @Transactional
    public Role handle(UpdateRoleCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Role existingRole = roleRepository.findById(command.roleId())
                .orElseThrow(() -> new RoleNotFoundException(command.roleId()));
        Module module = roleManagementSupport.getModule(existingRole.moduleId());
        roleManagementSupport.validateRoleName(module, command.name());
        roleManagementSupport.validatePermissions(existingRole.moduleId(), command.permissionIds());

        String normalizedName = command.name().trim();
        if (!existingRole.name().equals(normalizedName)
                && roleRepository.existsByModuleIdAndName(existingRole.moduleId(), normalizedName)) {
            throw new RoleConflictException(normalizedName);
        }

        Role updatedRole = existingRole.update(
                normalizedName,
                command.description(),
                new LinkedHashSet<>(command.permissionIds())
        );

        try {
            return roleRepository.save(updatedRole);
        } catch (DataIntegrityViolationException exception) {
            throw new RoleConflictException(normalizedName);
        }
    }
}
