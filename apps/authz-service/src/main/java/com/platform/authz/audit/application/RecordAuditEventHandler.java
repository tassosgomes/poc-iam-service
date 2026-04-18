package com.platform.authz.audit.application;

import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordAuditEventHandler implements RecordAuditEvent {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecordAuditEventHandler.class);

    private final AuditEventRepository auditEventRepository;

    public RecordAuditEventHandler(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = Objects.requireNonNull(auditEventRepository, "auditEventRepository must not be null");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        AuditEvent persistedEvent = auditEventRepository.save(event);
        LOGGER.info(
                "audit_event_recorded eventType={} actorId={} target={} sourceIp={} occurredAt={} payload={}",
                persistedEvent.eventType(),
                persistedEvent.actorId(),
                persistedEvent.target(),
                persistedEvent.sourceIp(),
                persistedEvent.occurredAt(),
                persistedEvent.payload()
        );
    }
}
