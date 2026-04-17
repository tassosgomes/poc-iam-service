package com.platform.authz.modules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.audit.application.RecordAuditEvent;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleAlreadyExistsException;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyHasher;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CreateModuleHandlerTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleKeyRepository moduleKeyRepository;

    @Mock
    private ModuleKeyHasher moduleKeyHasher;

    @Mock
    private RecordAuditEvent recordAuditEvent;

    private CreateModuleHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CreateModuleHandler(
                moduleRepository,
                moduleKeyRepository,
                moduleKeyHasher,
                recordAuditEvent,
                Clock.fixed(Instant.parse("2026-04-17T04:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void handle_WithValidCommand_ShouldPersistModuleAndReturnPlainSecret() {
        // Arrange
        CreateModuleCommand command = new CreateModuleCommand(
                "Sales",
                "sales",
                "Sales module",
                "user-admin",
                "10.0.0.10"
        );
        when(moduleRepository.existsByAllowedPrefix("sales")).thenReturn(false);
        when(moduleRepository.existsByName("Sales")).thenReturn(false);
        when(moduleRepository.save(any(Module.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleKeyRepository.save(any(ModuleKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moduleKeyHasher.hash(any(String.class))).thenReturn("hashed-secret");

        ArgumentCaptor<Module> moduleCaptor = ArgumentCaptor.forClass(Module.class);
        ArgumentCaptor<ModuleKey> moduleKeyCaptor = ArgumentCaptor.forClass(ModuleKey.class);
        ArgumentCaptor<String> secretCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        CreateModuleResult result = handler.handle(command);

        // Assert
        verify(moduleRepository).save(moduleCaptor.capture());
        verify(moduleKeyRepository).save(moduleKeyCaptor.capture());
        verify(moduleKeyHasher).hash(secretCaptor.capture());
        verify(recordAuditEvent).record(auditEventCaptor.capture());

        Module persistedModule = moduleCaptor.getValue();
        ModuleKey persistedKey = moduleKeyCaptor.getValue();
        String rawSecret = secretCaptor.getValue();
        AuditEvent auditEvent = auditEventCaptor.getValue();

        assertThat(result.moduleId()).isEqualTo(persistedModule.id());
        assertThat(result.name()).isEqualTo("Sales");
        assertThat(result.allowedPrefix()).isEqualTo("sales");
        assertThat(result.secret()).isEqualTo(rawSecret);
        assertThat(Base64.getUrlDecoder().decode(rawSecret)).hasSize(32);

        assertThat(persistedModule.allowedPrefix()).isEqualTo("sales");
        assertThat(persistedModule.createdBy()).isEqualTo("user-admin");
        assertThat(persistedKey.moduleId()).isEqualTo(persistedModule.id());
        assertThat(persistedKey.keyHash()).isEqualTo("hashed-secret");
        assertThat(auditEvent.eventType()).isEqualTo(AuditEventType.MODULE_CREATED);
        assertThat(auditEvent.actorId()).isEqualTo("user-admin");
        assertThat(auditEvent.target()).isEqualTo(persistedModule.id().toString());
        assertThat(auditEvent.sourceIp()).isEqualTo("10.0.0.10");
        assertThat(auditEvent.payload()).containsEntry("moduleId", persistedModule.id().toString());
        assertThat(auditEvent.payload()).containsEntry("keyId", persistedKey.id().toString());
        assertThat(auditEvent.payload()).containsEntry("allowedPrefix", "sales");
        assertThat(auditEvent.payload()).containsKey("payloadHash");
    }

    @Test
    void handle_WithDuplicatedAllowedPrefix_ShouldThrowConflictException() {
        // Arrange
        CreateModuleCommand command = new CreateModuleCommand(
                "Sales",
                "sales",
                "Sales module",
                "user-admin",
                "10.0.0.10"
        );
        when(moduleRepository.existsByAllowedPrefix("sales")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(ModuleAlreadyExistsException.class)
                .hasMessageContaining("allowedPrefix");

        verify(moduleRepository, never()).save(any(Module.class));
        verify(moduleKeyRepository, never()).save(any(ModuleKey.class));
        verify(recordAuditEvent, never()).record(any(AuditEvent.class));
    }

    @Test
    void handle_WhenRepositoryDetectsConcurrentDuplicate_ShouldTranslateConflict() {
        // Arrange
        CreateModuleCommand command = new CreateModuleCommand(
                "Sales",
                "sales",
                "Sales module",
                "user-admin",
                "10.0.0.10"
        );
        when(moduleRepository.existsByAllowedPrefix("sales")).thenReturn(false);
        when(moduleRepository.existsByName("Sales")).thenReturn(false);
        when(moduleRepository.save(any(Module.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(com.platform.authz.modules.domain.ModuleConflictException.class)
                .hasMessage("Module name or allowedPrefix already exists");

        verify(moduleKeyRepository, never()).save(any(ModuleKey.class));
        verify(recordAuditEvent, never()).record(any(AuditEvent.class));
    }
}
