package com.platform.authz.modules.application;

import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetModuleHandler {
    private final ModuleRepository moduleRepository;
    private final ModuleKeyRepository moduleKeyRepository;
    private final ModuleSummaryProjector moduleSummaryProjector;
    private final Clock clock;

    public GetModuleHandler(
            ModuleRepository moduleRepository,
            ModuleKeyRepository moduleKeyRepository,
            ModuleSummaryProjector moduleSummaryProjector,
            Clock clock
    ) {
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.moduleKeyRepository = Objects.requireNonNull(moduleKeyRepository, "moduleKeyRepository must not be null");
        this.moduleSummaryProjector = Objects.requireNonNull(
                moduleSummaryProjector,
                "moduleSummaryProjector must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public ModuleSummary handle(UUID moduleId) {
        Objects.requireNonNull(moduleId, "moduleId must not be null");

        Module module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ModuleNotFoundException(moduleId));

        return moduleSummaryProjector.project(
                module,
                moduleKeyRepository.findActiveByModuleId(moduleId),
                Instant.now(clock)
        );
    }
}
