package com.platform.authz.iam.api;

import com.platform.authz.iam.api.dto.UserPermissionsDto;
import com.platform.authz.iam.application.GetUserPermissionsHandler;
import com.platform.authz.iam.application.GetUserPermissionsQuery;
import com.platform.authz.iam.application.UserPermissions;
import com.platform.authz.shared.security.ModuleScopeExtractor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/users/{userId}/permissions")
public class PermissionQueryController {

    private final GetUserPermissionsHandler getUserPermissionsHandler;
    private final ModuleScopeExtractor moduleScopeExtractor;
    private final Timer bulkFetchTimer;

    public PermissionQueryController(
            GetUserPermissionsHandler getUserPermissionsHandler,
            ModuleScopeExtractor moduleScopeExtractor,
            MeterRegistry meterRegistry
    ) {
        this.getUserPermissionsHandler = Objects.requireNonNull(
                getUserPermissionsHandler,
                "getUserPermissionsHandler must not be null"
        );
        this.moduleScopeExtractor = Objects.requireNonNull(
                moduleScopeExtractor,
                "moduleScopeExtractor must not be null"
        );
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.bulkFetchTimer = Timer.builder("authz_bulk_fetch_seconds")
                .description("Latency of user permissions bulk fetch")
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(5),
                        Duration.ofMillis(10),
                        Duration.ofMillis(25),
                        Duration.ofMillis(50),
                        Duration.ofMillis(100),
                        Duration.ofMillis(250)
                )
                .register(meterRegistry);
    }

    @GetMapping
    public ResponseEntity<UserPermissionsDto> getUserPermissions(
            @PathVariable("userId") @NotBlank String userId,
            Authentication authentication
    ) {
        return bulkFetchTimer.record(() -> {
            assertCanAccessPermissions(userId, authentication);

            UserPermissions result = getUserPermissionsHandler.handle(
                    new GetUserPermissionsQuery(userId)
            );

            return ResponseEntity.ok(toDto(result));
        });
    }

    private void assertCanAccessPermissions(String userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String authenticatedUser = authentication.getName();

        if (userId.equals(authenticatedUser)) {
            return;
        }

        if (moduleScopeExtractor.isPlatformAdmin(authentication)) {
            return;
        }

        throw new AccessDeniedException(
                "Users can only access their own permissions; PLATFORM_ADMIN can access any user's permissions"
        );
    }

    private static UserPermissionsDto toDto(UserPermissions userPermissions) {
        return new UserPermissionsDto(
                userPermissions.userId(),
                List.copyOf(userPermissions.permissions()),
                userPermissions.resolvedAt(),
                userPermissions.ttl().toSeconds()
        );
    }
}
