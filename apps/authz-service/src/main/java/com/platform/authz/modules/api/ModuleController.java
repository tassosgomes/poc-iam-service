package com.platform.authz.modules.api;

import com.platform.authz.modules.api.dto.CreateModuleRequest;
import com.platform.authz.modules.api.dto.CreateModuleResponse;
import com.platform.authz.modules.api.dto.ModuleSummaryDto;
import com.platform.authz.modules.application.CreateModuleCommand;
import com.platform.authz.modules.application.CreateModuleHandler;
import com.platform.authz.modules.application.CreateModuleResult;
import com.platform.authz.modules.application.GetModuleHandler;
import com.platform.authz.modules.application.ListModulesHandler;
import com.platform.authz.modules.application.ModuleSummary;
import com.platform.authz.shared.api.RequestMetadataResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/modules")
public class ModuleController {
    private final CreateModuleHandler createModuleHandler;
    private final ListModulesHandler listModulesHandler;
    private final GetModuleHandler getModuleHandler;
    private final PlatformAdminAccessEvaluator platformAdminAccessEvaluator;
    private final RequestMetadataResolver requestMetadataResolver;

    public ModuleController(
            CreateModuleHandler createModuleHandler,
            ListModulesHandler listModulesHandler,
            GetModuleHandler getModuleHandler,
            PlatformAdminAccessEvaluator platformAdminAccessEvaluator,
            RequestMetadataResolver requestMetadataResolver
    ) {
        this.createModuleHandler = Objects.requireNonNull(createModuleHandler, "createModuleHandler must not be null");
        this.listModulesHandler = Objects.requireNonNull(listModulesHandler, "listModulesHandler must not be null");
        this.getModuleHandler = Objects.requireNonNull(getModuleHandler, "getModuleHandler must not be null");
        this.platformAdminAccessEvaluator = Objects.requireNonNull(
                platformAdminAccessEvaluator,
                "platformAdminAccessEvaluator must not be null"
        );
        this.requestMetadataResolver = Objects.requireNonNull(
                requestMetadataResolver,
                "requestMetadataResolver must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<CreateModuleResponse> createModule(
            @Valid @RequestBody CreateModuleRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        platformAdminAccessEvaluator.assertPlatformAdmin(authentication);

        CreateModuleResult result = createModuleHandler.handle(new CreateModuleCommand(
                request.name(),
                request.allowedPrefix(),
                request.description(),
                requestMetadataResolver.resolveActor(authentication),
                requestMetadataResolver.resolveSourceIp(httpServletRequest)
        ));

        return ResponseEntity.created(URI.create("/v1/modules/" + result.moduleId()))
                .body(new CreateModuleResponse(
                        result.moduleId(),
                        result.name(),
                        result.allowedPrefix(),
                        result.secret(),
                        result.createdAt()
                ));
    }

    @GetMapping
    public ResponseEntity<List<ModuleSummaryDto>> listModules(Authentication authentication) {
        platformAdminAccessEvaluator.assertPlatformAdmin(authentication);

        List<ModuleSummaryDto> modules = listModulesHandler.handle().stream()
                .map(this::toDto)
                .toList();

        return ResponseEntity.ok(modules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModuleSummaryDto> getModule(
            @PathVariable("id") UUID moduleId,
            Authentication authentication
    ) {
        platformAdminAccessEvaluator.assertPlatformAdmin(authentication);
        return ResponseEntity.ok(toDto(getModuleHandler.handle(moduleId)));
    }

    private ModuleSummaryDto toDto(ModuleSummary summary) {
        return new ModuleSummaryDto(
                summary.moduleId(),
                summary.name(),
                summary.allowedPrefix(),
                summary.description(),
                summary.createdAt(),
                summary.lastHeartbeatAt(),
                summary.heartbeatStatus(),
                summary.activeKeyId(),
                summary.activeKeyStatus(),
                summary.activeKeyCreatedAt(),
                summary.activeKeyAgeDays()
        );
    }
}
