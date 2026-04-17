package com.platform.authz.catalog.api;

import com.platform.authz.catalog.api.dto.SyncRequest;
import com.platform.authz.catalog.api.dto.SyncResponse;
import com.platform.authz.catalog.application.SyncCatalogCommand;
import com.platform.authz.catalog.application.SyncCatalogHandler;
import com.platform.authz.catalog.application.SyncCatalogResult;
import com.platform.authz.shared.exception.InvalidModuleAuthenticationException;
import com.platform.authz.shared.security.ModuleAuthenticationToken;
import com.platform.authz.shared.security.ModuleContext;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/catalog")
public class CatalogController {

    private final SyncCatalogHandler syncCatalogHandler;

    public CatalogController(SyncCatalogHandler syncCatalogHandler) {
        this.syncCatalogHandler = Objects.requireNonNull(syncCatalogHandler, "syncCatalogHandler must not be null");
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> sync(
            @Valid @RequestBody SyncRequest request,
            Authentication authentication
    ) {
        ModuleContext moduleContext = extractModuleContext(authentication);

        SyncCatalogCommand command = new SyncCatalogCommand(
                request.moduleId(),
                request.schemaVersion(),
                request.payloadHash(),
                request.permissions().stream()
                        .map(p -> new SyncCatalogCommand.PermissionEntry(p.code(), p.description()))
                        .toList()
        );

        SyncCatalogResult result = syncCatalogHandler.handle(command, moduleContext);

        return ResponseEntity.ok(new SyncResponse(
                result.catalogVersion(),
                result.added(),
                result.updated(),
                result.deprecated(),
                result.changed()
        ));
    }

    private ModuleContext extractModuleContext(Authentication authentication) {
        if (authentication instanceof ModuleAuthenticationToken moduleAuth) {
            return moduleAuth.getPrincipal();
        }
        String actualType = authentication != null ? authentication.getClass().getSimpleName() : "null";
        throw new InvalidModuleAuthenticationException(actualType);
    }
}
