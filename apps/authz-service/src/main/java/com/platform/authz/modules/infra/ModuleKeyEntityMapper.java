package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.ModuleKey;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ModuleKeyEntityMapper {

    ModuleKey toDomain(ModuleKeyEntity entity);

    ModuleKeyEntity toEntity(ModuleKey moduleKey);
}
