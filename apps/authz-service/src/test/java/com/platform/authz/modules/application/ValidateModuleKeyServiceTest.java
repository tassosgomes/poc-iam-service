package com.platform.authz.modules.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyHasher;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleKeyStatus;
import com.platform.authz.modules.domain.ModuleRepository;
import com.platform.authz.shared.exception.UnauthorizedModuleKeyException;
import com.platform.authz.shared.security.ModuleContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidateModuleKeyServiceTest {

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private ModuleKeyRepository moduleKeyRepository;

    @Mock
    private ModuleKeyHasher moduleKeyHasher;

    private ValidateModuleKeyService service;

    @BeforeEach
    void setUp() {
        service = new ValidateModuleKeyService(
                moduleRepository,
                moduleKeyRepository,
                moduleKeyHasher,
                Clock.fixed(Instant.parse("2026-04-17T15:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void validate_WithActiveKey_ShouldReturnModuleContext() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = module(moduleId, "sales");
        ModuleKey activeKey = ModuleKey.createActive(moduleId, "active-hash", Instant.parse("2026-04-16T10:00:00Z"));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleKeyRepository.findActiveOrInGraceByModuleId(moduleId, Instant.parse("2026-04-17T15:00:00Z")))
                .thenReturn(List.of(activeKey));
        when(moduleKeyHasher.matches("plain-secret", "active-hash")).thenReturn(true);

        // Act
        ModuleContext result = service.validate(moduleId.toString(), "plain-secret");

        // Assert
        assertThat(result.moduleId()).isEqualTo(moduleId.toString());
        assertThat(result.allowedPrefix()).isEqualTo("sales");
        assertThat(result.keyIssuedAt()).isEqualTo(Instant.parse("2026-04-16T10:00:00Z"));
        verify(moduleKeyRepository, never()).findByModuleId(moduleId);
    }

    @Test
    void validate_WithSupersededKeyInGrace_ShouldAcceptKey() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = module(moduleId, "sales");
        ModuleKey supersededKey = new ModuleKey(
                UUID.randomUUID(),
                moduleId,
                "grace-hash",
                ModuleKeyStatus.SUPERSEDED,
                Instant.parse("2026-04-17T14:00:00Z"),
                Instant.parse("2026-04-18T14:00:00Z"),
                Instant.parse("2026-04-16T09:00:00Z")
        );
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleKeyRepository.findActiveOrInGraceByModuleId(moduleId, Instant.parse("2026-04-17T15:00:00Z")))
                .thenReturn(List.of(supersededKey));
        when(moduleKeyHasher.matches("rotated-secret", "grace-hash")).thenReturn(true);

        // Act
        ModuleContext result = service.validate(moduleId.toString(), "rotated-secret");

        // Assert
        assertThat(result.moduleId()).isEqualTo(moduleId.toString());
        assertThat(result.allowedPrefix()).isEqualTo("sales");
    }

    @Test
    void validate_WithExpiredGraceKeysOnly_ShouldThrowUnauthorized() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = module(moduleId, "sales");
        ModuleKey expiredKey = new ModuleKey(
                UUID.randomUUID(),
                moduleId,
                "expired-hash",
                ModuleKeyStatus.SUPERSEDED,
                Instant.parse("2026-04-16T10:00:00Z"),
                Instant.parse("2026-04-17T14:59:59Z"),
                Instant.parse("2026-04-15T10:00:00Z")
        );
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleKeyRepository.findActiveOrInGraceByModuleId(moduleId, Instant.parse("2026-04-17T15:00:00Z")))
                .thenReturn(List.of());
        when(moduleKeyRepository.findByModuleId(moduleId)).thenReturn(List.of(expiredKey));

        // Act & Assert
        assertThatThrownBy(() -> service.validate(moduleId.toString(), "expired-secret"))
                .isInstanceOf(UnauthorizedModuleKeyException.class)
                .extracting(exception -> ((UnauthorizedModuleKeyException) exception).reason())
                .isEqualTo("expired_grace");
    }

    @Test
    void validate_WithRevokedKeysOnly_ShouldThrowUnauthorized() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        Module module = module(moduleId, "sales");
        ModuleKey revokedKey = new ModuleKey(
                UUID.randomUUID(),
                moduleId,
                "revoked-hash",
                ModuleKeyStatus.REVOKED,
                null,
                null,
                Instant.parse("2026-04-15T10:00:00Z")
        );
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module));
        when(moduleKeyRepository.findActiveOrInGraceByModuleId(moduleId, Instant.parse("2026-04-17T15:00:00Z")))
                .thenReturn(List.of());
        when(moduleKeyRepository.findByModuleId(moduleId)).thenReturn(List.of(revokedKey));

        // Act & Assert
        assertThatThrownBy(() -> service.validate(moduleId.toString(), "revoked-secret"))
                .isInstanceOf(UnauthorizedModuleKeyException.class)
                .extracting(exception -> ((UnauthorizedModuleKeyException) exception).reason())
                .isEqualTo("revoked");
    }

    @Test
    void validate_WithUnknownModuleId_ShouldThrowUnauthorized() {
        // Arrange
        UUID moduleId = UUID.randomUUID();
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.validate(moduleId.toString(), "missing-secret"))
                .isInstanceOf(UnauthorizedModuleKeyException.class)
                .extracting(exception -> ((UnauthorizedModuleKeyException) exception).reason())
                .isEqualTo("not_found");
    }

    private Module module(UUID moduleId, String allowedPrefix) {
        return new Module(
                moduleId,
                "Sales",
                allowedPrefix,
                "Sales module",
                "user-admin",
                Instant.parse("2026-04-15T00:00:00Z"),
                null
        );
    }
}
