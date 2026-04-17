package com.platform.authz.audit.infra;

import com.platform.authz.audit.application.RecordAuditEvent;
import com.platform.authz.audit.domain.AuditEvent;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class JpaRecordAuditEvent implements RecordAuditEvent {
    private final SpringDataAuditEventRepository springDataAuditEventRepository;
    private final AuditEventEntityMapper auditEventEntityMapper;

    public JpaRecordAuditEvent(
            SpringDataAuditEventRepository springDataAuditEventRepository,
            AuditEventEntityMapper auditEventEntityMapper
    ) {
        this.springDataAuditEventRepository = Objects.requireNonNull(
                springDataAuditEventRepository,
                "springDataAuditEventRepository must not be null"
        );
        this.auditEventEntityMapper = Objects.requireNonNull(
                auditEventEntityMapper,
                "auditEventEntityMapper must not be null"
        );
    }

    @Override
    public void record(AuditEvent event) {
        springDataAuditEventRepository.save(auditEventEntityMapper.toEntity(event));
    }
}
