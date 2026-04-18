package com.platform.authz.audit.application;

import com.platform.authz.audit.domain.AuditEventPage;
import com.platform.authz.audit.domain.AuditEventRepository;
import com.platform.authz.audit.domain.AuditEventSearchCriteria;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAuditEventsHandler {

    private final AuditEventRepository auditEventRepository;

    public ListAuditEventsHandler(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = Objects.requireNonNull(auditEventRepository, "auditEventRepository must not be null");
    }

    @Transactional(readOnly = true)
    public AuditEventPage handle(ListAuditEventsQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        return auditEventRepository.findPage(new AuditEventSearchCriteria(
                query.eventType(),
                query.moduleId(),
                query.actorId(),
                query.from(),
                query.to(),
                query.page(),
                query.size()
        ));
    }
}
