package com.platform.authz.iam.api;

import com.platform.authz.iam.api.dto.AssignRoleRequest;
import com.platform.authz.iam.api.dto.UserRoleDto;
import com.platform.authz.iam.application.AssignRoleCommand;
import com.platform.authz.iam.application.AssignRoleHandler;
import com.platform.authz.iam.application.AssignRoleResult;
import com.platform.authz.iam.application.ListUserRolesQuery;
import com.platform.authz.iam.application.RevokeRoleCommand;
import com.platform.authz.iam.application.RevokeRoleHandler;
import com.platform.authz.iam.application.UserRoleView;
import com.platform.authz.shared.api.RequestMetadataResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/users/{userId}/roles")
public class UserRoleController {

    private final AssignRoleHandler assignRoleHandler;
    private final RevokeRoleHandler revokeRoleHandler;
    private final ListUserRolesQuery listUserRolesQuery;
    private final RequestMetadataResolver requestMetadataResolver;

    public UserRoleController(
            AssignRoleHandler assignRoleHandler,
            RevokeRoleHandler revokeRoleHandler,
            ListUserRolesQuery listUserRolesQuery,
            RequestMetadataResolver requestMetadataResolver
    ) {
        this.assignRoleHandler = Objects.requireNonNull(assignRoleHandler, "assignRoleHandler must not be null");
        this.revokeRoleHandler = Objects.requireNonNull(revokeRoleHandler, "revokeRoleHandler must not be null");
        this.listUserRolesQuery = Objects.requireNonNull(listUserRolesQuery, "listUserRolesQuery must not be null");
        this.requestMetadataResolver = Objects.requireNonNull(
                requestMetadataResolver,
                "requestMetadataResolver must not be null"
        );
    }

    @PostMapping
    @PreAuthorize("@adminScopeChecker.hasManagementAccess(authentication)")
    public ResponseEntity<UserRoleDto> assignRole(
            @PathVariable("userId") @NotBlank String userId,
            @Valid @RequestBody AssignRoleRequest request,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        AssignRoleResult result = assignRoleHandler.handle(
                new AssignRoleCommand(
                        userId,
                        request.roleId(),
                        requestMetadataResolver.resolveActor(authentication),
                        requestMetadataResolver.resolveSourceIp(httpServletRequest)
                ),
                authentication
        );

        if (result.created()) {
            return ResponseEntity.created(URI.create("/v1/users/" + userId + "/roles/" + request.roleId()))
                    .body(toDto(result.assignment()));
        }

        return ResponseEntity.ok(toDto(result.assignment()));
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("@adminScopeChecker.hasManagementAccess(authentication)")
    public ResponseEntity<Void> revokeRole(
            @PathVariable("userId") @NotBlank String userId,
            @PathVariable("roleId") UUID roleId,
            Authentication authentication,
            HttpServletRequest httpServletRequest
    ) {
        revokeRoleHandler.handle(
                new RevokeRoleCommand(
                        userId,
                        roleId,
                        requestMetadataResolver.resolveActor(authentication),
                        requestMetadataResolver.resolveSourceIp(httpServletRequest)
                ),
                authentication
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("@adminScopeChecker.hasManagementAccess(authentication)")
    public ResponseEntity<List<UserRoleDto>> listUserRoles(
            @PathVariable("userId") @NotBlank String userId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                listUserRolesQuery.handle(userId, authentication).stream()
                        .map(UserRoleController::toDto)
                        .toList()
        );
    }

    private static UserRoleDto toDto(UserRoleView userRoleView) {
        return new UserRoleDto(
                userRoleView.userId(),
                userRoleView.roleId(),
                userRoleView.moduleId(),
                userRoleView.roleName(),
                userRoleView.assignedBy(),
                userRoleView.assignedAt()
        );
    }
}
