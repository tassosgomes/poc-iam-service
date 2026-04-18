package com.platform.authz.audit.infra;

import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventPage;
import com.platform.authz.audit.domain.AuditEventRepository;
import com.platform.authz.audit.domain.AuditEventSearchCriteria;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditEventRepository implements AuditEventRepository {

    private final SpringDataAuditEventJpaRepository springDataAuditEventJpaRepository;
    private final AuditEventJpaEntityMapper auditEventJpaEntityMapper;

    public JpaAuditEventRepository(
            SpringDataAuditEventJpaRepository springDataAuditEventJpaRepository,
            AuditEventJpaEntityMapper auditEventJpaEntityMapper
    ) {
        this.springDataAuditEventJpaRepository = Objects.requireNonNull(
                springDataAuditEventJpaRepository,
                "springDataAuditEventJpaRepository must not be null"
        );
        this.auditEventJpaEntityMapper = Objects.requireNonNull(
                auditEventJpaEntityMapper,
                "auditEventJpaEntityMapper must not be null"
        );
    }

    @Override
    public AuditEvent save(AuditEvent auditEvent) {
        AuditEventJpaEntity savedEntity = springDataAuditEventJpaRepository.saveAndFlush(
                auditEventJpaEntityMapper.toEntity(auditEvent)
        );
        return auditEventJpaEntityMapper.toDomain(savedEntity);
    }

    @Override
    public AuditEventPage findPage(AuditEventSearchCriteria criteria) {
        Objects.requireNonNull(criteria, "criteria must not be null");

        var page = springDataAuditEventJpaRepository.search(
                criteria.eventType() != null ? criteria.eventType().name() : null,
                criteria.moduleId() != null ? criteria.moduleId().toString() : null,
                normalize(criteria.actorId()),
                criteria.from(),
                criteria.to(),
                PageRequest.of(criteria.page() - 1, criteria.size())
        );

        return new AuditEventPage(
                page.getContent().stream().map(auditEventJpaEntityMapper::toDomain).toList(),
                page.getTotalElements()
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
