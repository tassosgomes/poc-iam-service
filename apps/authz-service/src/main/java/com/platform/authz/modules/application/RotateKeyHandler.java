package com.platform.authz.modules.application;

import com.platform.authz.audit.application.AuditEventPublisher;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleActiveKeyNotFoundException;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyHasher;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RotateKeyHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotateKeyHandler.class);
    private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofHours(24);

    private final ModuleRepository moduleRepository;
    private final ModuleKeyRepository moduleKeyRepository;
    private final ModuleKeyHasher moduleKeyHasher;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public RotateKeyHandler(
            ModuleRepository moduleRepository,
            ModuleKeyRepository moduleKeyRepository,
            ModuleKeyHasher moduleKeyHasher,
            AuditEventPublisher auditEventPublisher,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.moduleKeyRepository = Objects.requireNonNull(moduleKeyRepository, "moduleKeyRepository must not be null");
        this.moduleKeyHasher = Objects.requireNonNull(moduleKeyHasher, "moduleKeyHasher must not be null");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public RotateKeyResult handle(RotateKeyCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        Module module = moduleRepository.findById(command.moduleId())
                .orElseThrow(() -> new ModuleNotFoundException(command.moduleId()));
        ModuleKey activeKey = moduleKeyRepository.findActiveByModuleIdForUpdate(command.moduleId())
                .orElseThrow(() -> new ModuleActiveKeyNotFoundException(command.moduleId()));

        Instant now = Instant.now(clock);
        Instant graceExpiresAt = now.plus(DEFAULT_GRACE_PERIOD);

        ModuleKey supersededKey = activeKey.supersede(now, graceExpiresAt);

        String secret = generateSecret();
        String keyHash = moduleKeyHasher.hash(secret);
        ModuleKey rotatedKey = ModuleKey.createActive(module.id(), keyHash, now);

        moduleKeyRepository.saveAndFlush(supersededKey);
        moduleKeyRepository.save(rotatedKey);
        auditEventPublisher.publish(buildKeyRotatedAuditEvent(command, rotatedKey, graceExpiresAt));

        LOGGER.info("module.key-rotated moduleId={} keyId={} actor={}", module.id(), rotatedKey.id(), command.rotatedBy());

        return new RotateKeyResult(
                module.id(),
                rotatedKey.id(),
                secret,
                rotatedKey.createdAt(),
                graceExpiresAt
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

    private AuditEvent buildKeyRotatedAuditEvent(
            RotateKeyCommand command,
            ModuleKey rotatedKey,
            Instant graceExpiresAt
    ) {
        String payloadHash = hashPayload(
                AuditEventType.KEY_ROTATED.name(),
                command.moduleId().toString(),
                rotatedKey.id().toString(),
                rotatedKey.createdAt().toString(),
                graceExpiresAt.toString()
        );

        return new AuditEvent(
                UUID.randomUUID(),
                AuditEventType.KEY_ROTATED,
                command.rotatedBy(),
                command.moduleId().toString(),
                Map.of(
                        "moduleId", command.moduleId().toString(),
                        "keyId", rotatedKey.id().toString(),
                        "graceExpiresAt", graceExpiresAt.toString(),
                        "payloadHash", payloadHash
                ),
                normalizeSourceIp(command.sourceIp()),
                rotatedKey.createdAt()
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
