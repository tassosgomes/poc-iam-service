package com.platform.authz.modules.api;

import com.platform.authz.modules.api.dto.RotateKeyResponse;
import com.platform.authz.modules.application.RotateKeyCommand;
import com.platform.authz.modules.application.RotateKeyHandler;
import com.platform.authz.modules.application.RotateKeyResult;
import com.platform.authz.shared.api.RequestMetadataResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/modules/{id}/keys")
public class KeyController {
    private final RotateKeyHandler rotateKeyHandler;
    private final PlatformAdminAccessEvaluator platformAdminAccessEvaluator;
    private final RequestMetadataResolver requestMetadataResolver;

    public KeyController(
            RotateKeyHandler rotateKeyHandler,
            PlatformAdminAccessEvaluator platformAdminAccessEvaluator,
            RequestMetadataResolver requestMetadataResolver
    ) {
        this.rotateKeyHandler = Objects.requireNonNull(rotateKeyHandler, "rotateKeyHandler must not be null");
        this.platformAdminAccessEvaluator = Objects.requireNonNull(
                platformAdminAccessEvaluator,
                "platformAdminAccessEvaluator must not be null"
        );
        this.requestMetadataResolver = Objects.requireNonNull(
                requestMetadataResolver,
                "requestMetadataResolver must not be null"
        );
    }

    @PostMapping("/rotate")
    public ResponseEntity<RotateKeyResponse> rotateKey(
            @PathVariable("id") UUID moduleId,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        platformAdminAccessEvaluator.assertPlatformAdmin(authentication);

        RotateKeyResult result = rotateKeyHandler.handle(new RotateKeyCommand(
                moduleId,
                requestMetadataResolver.resolveActor(authentication),
                requestMetadataResolver.resolveSourceIp(httpServletRequest)
        ));

        return ResponseEntity.ok(new RotateKeyResponse(
                result.moduleId(),
                result.keyId(),
                result.secret(),
                result.createdAt(),
                result.graceExpiresAt()
        ));
    }
}
