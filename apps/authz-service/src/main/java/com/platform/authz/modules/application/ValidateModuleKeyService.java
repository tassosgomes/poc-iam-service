package com.platform.authz.modules.application;

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
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ValidateModuleKeyService {
    private final ModuleRepository moduleRepository;
    private final ModuleKeyRepository moduleKeyRepository;
    private final ModuleKeyHasher moduleKeyHasher;
    private final Clock clock;

    public ValidateModuleKeyService(
            ModuleRepository moduleRepository,
            ModuleKeyRepository moduleKeyRepository,
            ModuleKeyHasher moduleKeyHasher,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.moduleKeyRepository = Objects.requireNonNull(moduleKeyRepository, "moduleKeyRepository must not be null");
        this.moduleKeyHasher = Objects.requireNonNull(moduleKeyHasher, "moduleKeyHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public ModuleContext validate(String moduleIdHint, String secret) {
        Objects.requireNonNull(secret, "secret must not be null");

        UUID moduleId = parseModuleId(moduleIdHint);
        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new UnauthorizedModuleKeyException("not_found"));
        Instant now = Instant.now(clock);
        List<ModuleKey> candidateKeys = moduleKeyRepository.findActiveOrInGraceByModuleId(moduleId, now);

        if (candidateKeys.isEmpty()) {
            throw classifyUnavailableKeys(moduleId);
        }

        for (ModuleKey moduleKey : candidateKeys) {
            if (moduleKeyHasher.matches(secret, moduleKey.keyHash())) {
                return new ModuleContext(
                        module.id().toString(),
                        module.allowedPrefix(),
                        moduleKey.createdAt()
                );
            }
        }

        throw new UnauthorizedModuleKeyException("invalid");
    }

    private UUID parseModuleId(String moduleIdHint) {
        if (moduleIdHint == null || moduleIdHint.isBlank()) {
            throw new UnauthorizedModuleKeyException("missing_module_id");
        }

        try {
            return UUID.fromString(moduleIdHint);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedModuleKeyException("malformed_module_id");
        }
    }

    private UnauthorizedModuleKeyException classifyUnavailableKeys(UUID moduleId) {
        List<ModuleKey> allKeys = moduleKeyRepository.findByModuleId(moduleId);
        if (allKeys.isEmpty()) {
            return new UnauthorizedModuleKeyException("not_found");
        }

        if (allKeys.stream().anyMatch(this::isExpiredGraceKey)) {
            return new UnauthorizedModuleKeyException("expired_grace");
        }

        if (allKeys.stream().anyMatch(moduleKey -> moduleKey.status() == ModuleKeyStatus.REVOKED)) {
            return new UnauthorizedModuleKeyException("revoked");
        }

        return new UnauthorizedModuleKeyException("not_found");
    }

    private boolean isExpiredGraceKey(ModuleKey moduleKey) {
        return moduleKey.status() == ModuleKeyStatus.SUPERSEDED
                && moduleKey.graceExpiresAt() != null
                && !moduleKey.graceExpiresAt().isAfter(Instant.now(clock));
    }
}
