package com.platform.authz.audit.infra;

import com.platform.authz.audit.domain.AuditEvent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuditEventJpaEntityMapper {

    AuditEventJpaEntity toEntity(AuditEvent auditEvent);

    AuditEvent toDomain(AuditEventJpaEntity auditEventJpaEntity);
}
