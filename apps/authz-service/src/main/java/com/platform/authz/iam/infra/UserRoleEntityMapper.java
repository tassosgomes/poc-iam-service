package com.platform.authz.iam.infra;

import com.platform.authz.iam.domain.UserRoleAssignment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserRoleEntityMapper {

    UserRoleAssignment toDomain(UserRoleJpaEntity entity);

    UserRoleJpaEntity toEntity(UserRoleAssignment userRoleAssignment);
}
