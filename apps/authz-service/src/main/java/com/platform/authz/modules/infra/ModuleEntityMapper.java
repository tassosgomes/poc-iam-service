package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.Module;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ModuleEntityMapper {

    Module toDomain(ModuleEntity entity);

    ModuleEntity toEntity(Module module);
}
