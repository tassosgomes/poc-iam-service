package com.platform.authz.iam.infra;

import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RolePermission;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleEntityMapper {

    @Mapping(target = "permissions", expression = "java(mapPermissions(entity.getId(), entity.getPermissionIds()))")
    Role toDomain(RoleJpaEntity entity);

    @Mapping(target = "permissionIds", expression = "java(mapPermissionIds(role.permissions()))")
    RoleJpaEntity toEntity(Role role);

    default Set<RolePermission> mapPermissions(UUID roleId, Set<UUID> permissionIds) {
        LinkedHashSet<RolePermission> mappedPermissions = new LinkedHashSet<>();
        if (permissionIds == null) {
            return mappedPermissions;
        }

        permissionIds.forEach(permissionId -> mappedPermissions.add(new RolePermission(roleId, permissionId)));
        return mappedPermissions;
    }

    default Set<UUID> mapPermissionIds(Set<RolePermission> permissions) {
        LinkedHashSet<UUID> permissionIds = new LinkedHashSet<>();
        if (permissions == null) {
            return permissionIds;
        }

        permissions.forEach(permission -> permissionIds.add(permission.permissionId()));
        return permissionIds;
    }
}
