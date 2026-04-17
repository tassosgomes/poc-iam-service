package com.platform.authz.modules.application;

import com.platform.authz.audit.application.RecordAuditEvent;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleAlreadyExistsException;
import com.platform.authz.modules.domain.ModuleConflictException;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyHasher;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateModuleHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateModuleHandler.class);

    private final ModuleRepository moduleRepository;
    private final ModuleKeyRepository moduleKeyRepository;
    private final ModuleKeyHasher moduleKeyHasher;
    private final RecordAuditEvent recordAuditEvent;
    private final Clock clock;

    public CreateModuleHandler(
            ModuleRepository moduleRepository,
            ModuleKeyRepository moduleKeyRepository,
            ModuleKeyHasher moduleKeyHasher,
            RecordAuditEvent recordAuditEvent,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.moduleKeyRepository = Objects.requireNonNull(moduleKeyRepository, "moduleKeyRepository must not be null");
        this.moduleKeyHasher = Objects.requireNonNull(moduleKeyHasher, "moduleKeyHasher must not be null");
        this.recordAuditEvent = Objects.requireNonNull(recordAuditEvent, "recordAuditEvent must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public CreateModuleResult handle(CreateModuleCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        if (moduleRepository.existsByAllowedPrefix(command.allowedPrefix())) {
            throw new ModuleAlreadyExistsException("allowedPrefix", command.allowedPrefix());
        }

        if (moduleRepository.existsByName(command.name())) {
            throw new ModuleAlreadyExistsException("name", command.name());
        }

        Instant now = Instant.now(clock);
        Module module = Module.create(
                command.name(),
                command.allowedPrefix(),
                command.description(),
                command.createdBy(),
                now
        );
        Module persistedModule;

        try {
            persistedModule = moduleRepository.save(module);
        } catch (DataIntegrityViolationException exception) {
            throw new ModuleConflictException();
        }

        String secret = generateSecret();
        String keyHash = moduleKeyHasher.hash(secret);
        ModuleKey activeKey = ModuleKey.createActive(persistedModule.id(), keyHash, now);
        moduleKeyRepository.save(activeKey);
        recordAuditEvent.record(buildModuleCreatedAuditEvent(command, persistedModule, activeKey));

        LOGGER.info("module.created moduleId={} name={}", persistedModule.id(), persistedModule.name());

        return new CreateModuleResult(
                persistedModule.id(),
                persistedModule.name(),
                persistedModule.allowedPrefix(),
                secret,
                persistedModule.createdAt()
        );
    }

    private String generateSecret() {
        byte[] rawSecret = new byte[32];

        try {
            SecureRandom.getInstanceStrong().nextBytes(rawSecret);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Strong secure random algorithm is not available", exception);
        }

        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawSecret);
    }

    private AuditEvent buildModuleCreatedAuditEvent(
            CreateModuleCommand command,
            Module persistedModule,
            ModuleKey activeKey
    ) {
        String payloadHash = hashPayload(
                AuditEventType.MODULE_CREATED.name(),
                persistedModule.id().toString(),
                persistedModule.name(),
                persistedModule.allowedPrefix(),
                activeKey.id().toString(),
                persistedModule.createdAt().toString()
        );

        return new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.MODULE_CREATED,
                command.createdBy(),
                persistedModule.id().toString(),
                Map.of(
                        "moduleId", persistedModule.id().toString(),
                        "name", persistedModule.name(),
                        "allowedPrefix", persistedModule.allowedPrefix(),
                        "keyId", activeKey.id().toString(),
                        "payloadHash", payloadHash
                ),
                normalizeSourceIp(command.sourceIp()),
                persistedModule.createdAt()
        );
    }

    private String hashPayload(String... values) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            String canonicalPayload = String.join("|", values);
            return HexFormat.of().formatHex(messageDigest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private String normalizeSourceIp(String sourceIp) {
        return sourceIp == null || sourceIp.isBlank() ? null : sourceIp;
    }
}
