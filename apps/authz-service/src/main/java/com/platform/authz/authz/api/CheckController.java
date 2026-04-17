package com.platform.authz.authz.api;

import com.platform.authz.authz.api.dto.CheckRequest;
import com.platform.authz.authz.api.dto.CheckResponse;
import com.platform.authz.authz.application.CheckPermissionHandler;
import com.platform.authz.authz.application.CheckPermissionQuery;
import com.platform.authz.authz.application.CheckPermissionResult;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/authz/check")
public class CheckController {

    private static final String AUTHZ_CHECK_AUTHORITY = "ROLE_AUTHZ_CHECK";

    private final CheckPermissionHandler checkPermissionHandler;
    private final ModuleScopeExtractor moduleScopeExtractor;
    private final Timer checkTimer;

    public CheckController(
            CheckPermissionHandler checkPermissionHandler,
            ModuleScopeExtractor moduleScopeExtractor,
            MeterRegistry meterRegistry
    ) {
        this.checkPermissionHandler = Objects.requireNonNull(
                checkPermissionHandler,
                "checkPermissionHandler must not be null"
        );
        this.moduleScopeExtractor = Objects.requireNonNull(
                moduleScopeExtractor,
                "moduleScopeExtractor must not be null"
        );
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.checkTimer = Timer.builder("authz_check_seconds")
                .description("Latency of authz permission check")
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(1),
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50)
                )
                .register(meterRegistry);
    }

    @PostMapping
    public ResponseEntity<CheckResponse> check(
            @RequestBody @Valid CheckRequest request,
            Authentication authentication
    ) {
        return checkTimer.record(() -> {
            assertCanCheck(request.userId(), authentication);

            CheckPermissionResult result = checkPermissionHandler.handle(
                    new CheckPermissionQuery(request.userId(), request.permission())
            );

            return ResponseEntity.ok(new CheckResponse(result.allowed(), result.source()));
        });
    }

    private void assertCanCheck(String targetUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String authenticatedUser = authentication.getName();

        if (targetUserId.equals(authenticatedUser)) {
            return;
        }

        if (moduleScopeExtractor.isPlatformAdmin(authentication)) {
            return;
        }

        if (hasAuthzCheckRole(authentication)) {
            return;
        }

        throw new AccessDeniedException(
                "Users can only check their own permissions; "
                        + "PLATFORM_ADMIN or AUTHZ_CHECK role required to check other users"
        );
    }

    private boolean hasAuthzCheckRole(Authentication authentication) {
        Set<String> authorities = new java.util.LinkedHashSet<>();
        authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .forEach(authorities::add);
        return authorities.contains(AUTHZ_CHECK_AUTHORITY);
    }
}
