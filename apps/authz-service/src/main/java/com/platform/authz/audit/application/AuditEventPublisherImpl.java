package com.platform.authz.audit.application;

import com.platform.authz.audit.domain.AuditEvent;
import java.util.concurrent.Executor;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuditEventPublisherImpl implements AuditEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventPublisherImpl.class);

    private final RecordAuditEvent recordAuditEvent;
    private final Executor auditExecutor;

    public AuditEventPublisherImpl(
            RecordAuditEvent recordAuditEvent,
            @Qualifier("auditExecutor") Executor auditExecutor
    ) {
        this.recordAuditEvent = Objects.requireNonNull(recordAuditEvent, "recordAuditEvent must not be null");
        this.auditExecutor = Objects.requireNonNull(auditExecutor, "auditExecutor must not be null");
    }

    @Override
    public void publish(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        if (shouldPublishAfterCommit()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchAsync(event);
                }
            });
            return;
        }

        dispatchAsync(event);
    }

    private boolean shouldPublishAfterCommit() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    private void dispatchAsync(AuditEvent event) {
        try {
            auditExecutor.execute(() -> persistAuditEvent(event));
        } catch (Exception exception) {
            LOGGER.error(
                    "audit_event_publish_dispatch_failed eventType={} target={} actorId={}",
                    event.eventType(),
                    event.target(),
                    event.actorId(),
                    exception
            );
        }
    }

    private void persistAuditEvent(AuditEvent event) {
        try {
            recordAuditEvent.record(event);
        } catch (Exception exception) {
            LOGGER.error(
                    "audit_event_publish_failed eventType={} target={} actorId={}",
                    event.eventType(),
                    event.target(),
                    event.actorId(),
                    exception
            );
        }
    }
}
