package com.platform.authz.modules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.platform.authz.audit.application.RecordAuditEvent;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleActiveKeyNotFoundException;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyHasher;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleKeyStatus;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotateKeyHandlerTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleKeyRepository moduleKeyRepository;

    @Mock
    private ModuleKeyHasher moduleKeyHasher;

    @Mock
    private RecordAuditEvent recordAuditEvent;

    private RotateKeyHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RotateKeyHandler(
                moduleRepository,
                moduleKeyRepository,
                moduleKeyHasher,
                recordAuditEvent,
                Clock.fixed(Instant.parse("2026-04-17T05:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void handle_WithActiveKey_ShouldSupersedePreviousKeyAndReturnNewSecret() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = new Module(
                moduleId,
                "Sales",
                "sales",
                "Sales module",
                "user-admin",
                Instant.parse("2026-04-16T00:00:00Z"),
                null
        );
        ModuleKey activeKey = ModuleKey.createActive(moduleId, "previous-hash", Instant.parse("2026-04-16T00:00:00Z"));
        RotateKeyCommand command = new RotateKeyCommand(moduleId, "user-admin", "10.0.0.20");

        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(moduleKeyRepository.findActiveByModuleIdForUpdate(moduleId)).thenReturn(java.util.Optional.of(activeKey));
        when(moduleKeyRepository.saveAndFlush(any(ModuleKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleKeyRepository.save(any(ModuleKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleKeyHasher.hash(any(String.class))).thenReturn("next-hash");

        ArgumentCaptor<ModuleKey> supersededKeyCaptor = ArgumentCaptor.forClass(ModuleKey.class);
        ArgumentCaptor<ModuleKey> activeKeyCaptor = ArgumentCaptor.forClass(ModuleKey.class);
        ArgumentCaptor<String> secretCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        RotateKeyResult result = handler.handle(command);

        // Assert
        verify(moduleKeyRepository).saveAndFlush(supersededKeyCaptor.capture());
        verify(moduleKeyRepository).save(activeKeyCaptor.capture());
        verify(moduleKeyHasher).hash(secretCaptor.capture());
        verify(recordAuditEvent).record(auditEventCaptor.capture());

        ModuleKey supersededKey = supersededKeyCaptor.getValue();
        ModuleKey newActiveKey = activeKeyCaptor.getValue();
        AuditEvent auditEvent = auditEventCaptor.getValue();

        assertThat(supersededKey.id()).isEqualTo(activeKey.id());
        assertThat(supersededKey.status()).isEqualTo(ModuleKeyStatus.SUPERSEDED);
        assertThat(supersededKey.rotatedAt()).isEqualTo(Instant.parse("2026-04-17T05:00:00Z"));
        assertThat(supersededKey.graceExpiresAt()).isEqualTo(Instant.parse("2026-04-18T05:00:00Z"));

        assertThat(newActiveKey.status()).isEqualTo(ModuleKeyStatus.ACTIVE);
        assertThat(newActiveKey.keyHash()).isEqualTo("next-hash");
        assertThat(result.keyId()).isEqualTo(newActiveKey.id());
        assertThat(result.graceExpiresAt()).isEqualTo(Instant.parse("2026-04-18T05:00:00Z"));
        assertThat(result.secret()).isEqualTo(secretCaptor.getValue());
        assertThat(Base64.getUrlDecoder().decode(secretCaptor.getValue())).hasSize(32);
        assertThat(auditEvent.eventType()).isEqualTo(AuditEventType.KEY_ROTATED);
        assertThat(auditEvent.actorId()).isEqualTo("user-admin");
        assertThat(auditEvent.target()).isEqualTo(moduleId.toString());
        assertThat(auditEvent.sourceIp()).isEqualTo("10.0.0.20");
        assertThat(auditEvent.payload()).containsEntry("moduleId", moduleId.toString());
        assertThat(auditEvent.payload()).containsEntry("keyId", newActiveKey.id().toString());
        assertThat(auditEvent.payload()).containsEntry("graceExpiresAt", "2026-04-18T05:00:00Z");
        assertThat(auditEvent.payload()).containsKey("payloadHash");
    }

    @Test
    void handle_WithoutActiveKey_ShouldThrowException() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = new Module(
                moduleId,
                "Sales",
                "sales",
                "Sales module",
                "user-admin",
                Instant.parse("2026-04-16T00:00:00Z"),
                null
        );
        when(moduleRepository.findById(moduleId)).thenReturn(java.util.Optional.of(module));
        when(moduleKeyRepository.findActiveByModuleIdForUpdate(moduleId)).thenReturn(java.util.Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new RotateKeyCommand(moduleId, "user-admin", "10.0.0.20")))
                .isInstanceOf(ModuleActiveKeyNotFoundException.class);

        verify(recordAuditEvent, never()).record(any(AuditEvent.class));
    }
}
