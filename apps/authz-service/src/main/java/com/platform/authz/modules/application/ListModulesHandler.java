package com.platform.authz.modules.application;

import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleKey;
import com.platform.authz.modules.domain.ModuleKeyRepository;
import com.platform.authz.modules.domain.ModuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListModulesHandler {
    private final ModuleRepository moduleRepository;
    private final ModuleKeyRepository moduleKeyRepository;
    private final ModuleSummaryProjector moduleSummaryProjector;
    private final Clock clock;

    public ListModulesHandler(
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
    public List<ModuleSummary> handle() {
        Instant now = Instant.now(clock);
        List<Module> modules = moduleRepository.findAll();
        Map<UUID, ModuleKey> activeKeysByModuleId = moduleKeyRepository.findActiveByModuleIds(
                modules.stream().map(Module::id).toList()
        );

        return modules.stream()
                .map(module -> toSummary(module, activeKeysByModuleId, now))
                .toList();
    }

    private ModuleSummary toSummary(Module module, Map<UUID, ModuleKey> activeKeysByModuleId, Instant now) {
        return moduleSummaryProjector.project(
                module,
                Optional.ofNullable(activeKeysByModuleId.get(module.id())),
                now
        );
    }
}
