package com.platform.authz.audit.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventRepository;
import com.platform.authz.audit.domain.AuditEventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordAuditEventHandlerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    private RecordAuditEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RecordAuditEventHandler(auditEventRepository);
    }

    @Test
    void record_WithValidEvent_ShouldPersistAuditEvent() {
        // Arrange
        AuditEvent auditEvent = new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.ROLE_ASSIGNED,
                "admin-user",
                "user-123",
                Map.of("moduleId", UUID.randomUUID().toString(), "roleName", "VENDAS_GERENTE"),
                "10.0.0.10",
                Instant.parse("2026-04-17T21:10:00Z")
        );
        when(auditEventRepository.save(auditEvent)).thenReturn(auditEvent);

        // Act
        handler.record(auditEvent);

        // Assert
        verify(auditEventRepository).save(auditEvent);
    }
}
