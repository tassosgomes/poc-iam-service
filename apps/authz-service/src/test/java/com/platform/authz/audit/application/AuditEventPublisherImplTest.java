package com.platform.authz.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AuditEventPublisherImplTest {

    private final RecordAuditEvent recordAuditEvent = mock(RecordAuditEvent.class);
    private final Executor auditExecutor = mock(Executor.class);
    private final AuditEventPublisherImpl publisher = new AuditEventPublisherImpl(recordAuditEvent, auditExecutor);

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publish_WithoutActiveTransaction_ShouldDispatchImmediately() {
        // Arrange
        AuditEvent event = auditEvent();
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(auditExecutor).execute(any(Runnable.class));

        // Act
        publisher.publish(event);

        // Assert
        verify(auditExecutor).execute(any(Runnable.class));
        verify(recordAuditEvent).record(event);
    }

    @Test
    void publish_WithActiveTransaction_ShouldDispatchOnlyAfterCommit() {
        // Arrange
        AuditEvent event = auditEvent();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(auditExecutor).execute(any(Runnable.class));

        // Act
        publisher.publish(event);

        // Assert
        verifyNoInteractions(auditExecutor, recordAuditEvent);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(auditExecutor).execute(any(Runnable.class));
        verify(recordAuditEvent).record(event);
    }

    @Test
    void publish_WhenTransactionRollsBack_ShouldNotDispatchAuditEvent() {
        // Arrange
        AuditEvent event = auditEvent();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // Act
        publisher.publish(event);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // Assert
        verifyNoInteractions(auditExecutor, recordAuditEvent);
    }

    private AuditEvent auditEvent() {
        return new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_ASSIGNED,
                "admin-user",
                "user-123",
                Map.of("moduleId", UUID.randomUUID().toString()),
                "10.0.0.10",
                Instant.parse("2026-04-17T21:10:00Z")
        );
    }
}
