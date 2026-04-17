package com.platform.authz.iam.api;

import com.platform.authz.iam.api.dto.CloneRoleRequest;
import com.platform.authz.iam.api.dto.CreateRoleRequest;
import com.platform.authz.iam.api.dto.RoleDto;
import com.platform.authz.iam.api.dto.RolesPageDto;
import com.platform.authz.iam.api.dto.UpdateRoleRequest;
import com.platform.authz.iam.application.CloneRoleCommand;
import com.platform.authz.iam.application.CloneRoleHandler;
import com.platform.authz.iam.application.CreateRoleCommand;
import com.platform.authz.iam.application.CreateRoleHandler;
import com.platform.authz.iam.application.DeleteRoleHandler;
import com.platform.authz.iam.application.GetRoleHandler;
import com.platform.authz.iam.application.ListRolesHandler;
import com.platform.authz.iam.application.ListRolesQuery;
import com.platform.authz.iam.application.RolePermissionView;
import com.platform.authz.iam.application.RoleView;
import com.platform.authz.iam.application.RoleViewPage;
import com.platform.authz.iam.application.UpdateRoleCommand;
import com.platform.authz.iam.application.UpdateRoleHandler;
import com.platform.authz.shared.api.RequestMetadataResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/roles")
public class RoleController {

    private final CreateRoleHandler createRoleHandler;
    private final UpdateRoleHandler updateRoleHandler;
    private final CloneRoleHandler cloneRoleHandler;
    private final DeleteRoleHandler deleteRoleHandler;
    private final GetRoleHandler getRoleHandler;
    private final ListRolesHandler listRolesHandler;
    private final RoleAccessEvaluator roleAccessEvaluator;
    private final RequestMetadataResolver requestMetadataResolver;

    public RoleController(
            CreateRoleHandler createRoleHandler,
            UpdateRoleHandler updateRoleHandler,
            CloneRoleHandler cloneRoleHandler,
            DeleteRoleHandler deleteRoleHandler,
            GetRoleHandler getRoleHandler,
            ListRolesHandler listRolesHandler,
            RoleAccessEvaluator roleAccessEvaluator,
            RequestMetadataResolver requestMetadataResolver
    ) {
        this.createRoleHandler = Objects.requireNonNull(createRoleHandler, "createRoleHandler must not be null");
        this.updateRoleHandler = Objects.requireNonNull(updateRoleHandler, "updateRoleHandler must not be null");
        this.cloneRoleHandler = Objects.requireNonNull(cloneRoleHandler, "cloneRoleHandler must not be null");
        this.deleteRoleHandler = Objects.requireNonNull(deleteRoleHandler, "deleteRoleHandler must not be null");
        this.getRoleHandler = Objects.requireNonNull(getRoleHandler, "getRoleHandler must not be null");
        this.listRolesHandler = Objects.requireNonNull(listRolesHandler, "listRolesHandler must not be null");
        this.roleAccessEvaluator = Objects.requireNonNull(roleAccessEvaluator, "roleAccessEvaluator must not be null");
        this.requestMetadataResolver = Objects.requireNonNull(
                requestMetadataResolver,
                "requestMetadataResolver must not be null"
        );
    }

    @PostMapping
    public ResponseEntity<RoleDto> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            Authentication authentication
    ) {
        roleAccessEvaluator.assertCanManageModule(authentication, request.moduleId());

        UUID roleId = createRoleHandler.handle(new CreateRoleCommand(
                request.moduleId(),
                request.name(),
                request.description(),
                request.permissionIds(),
                requestMetadataResolver.resolveActor(authentication)
        )).id();

        RoleView response = getRoleHandler.handle(roleId);
        return ResponseEntity.created(URI.create("/v1/roles/" + roleId))
                .body(toDto(response));
    }

    @GetMapping
    public ResponseEntity<RolesPageDto> listRoles(
            @RequestParam("moduleId") @NotNull UUID moduleId,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "_page", defaultValue = "1") @Min(1) int page,
            @RequestParam(value = "_size", defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        roleAccessEvaluator.assertCanManageModule(authentication, moduleId);

        RoleViewPage response = listRolesHandler.handle(new ListRolesQuery(moduleId, query, page, size));
        long totalPages = response.totalElements() == 0 ? 0 : (long) Math.ceil((double) response.totalElements() / response.size());

        return ResponseEntity.ok(new RolesPageDto(
                response.roles().stream().map(RoleController::toDto).toList(),
                new RolesPageDto.PaginationDto(
                        response.page(),
                        response.size(),
                        response.totalElements(),
                        totalPages
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoleDto> getRole(
            @PathVariable("id") UUID roleId,
            Authentication authentication
    ) {
        roleAccessEvaluator.assertCanManageRole(authentication, roleId);
        return ResponseEntity.ok(toDto(getRoleHandler.handle(roleId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoleDto> updateRole(
            @PathVariable("id") UUID roleId,
            @Valid @RequestBody UpdateRoleRequest request,
            Authentication authentication
    ) {
        roleAccessEvaluator.assertCanManageRole(authentication, roleId);
        updateRoleHandler.handle(new UpdateRoleCommand(roleId, request.name(), request.description(), request.permissionIds()));
        return ResponseEntity.ok(toDto(getRoleHandler.handle(roleId)));
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<RoleDto> cloneRole(
            @PathVariable("id") UUID roleId,
            @Valid @RequestBody(required = false) CloneRoleRequest request,
            Authentication authentication
    ) {
        roleAccessEvaluator.assertCanManageRole(authentication, roleId);
        UUID clonedRoleId = cloneRoleHandler.handle(new CloneRoleCommand(
                roleId,
                request != null ? request.name() : null,
                requestMetadataResolver.resolveActor(authentication)
        )).id();

        return ResponseEntity.created(URI.create("/v1/roles/" + clonedRoleId))
                .body(toDto(getRoleHandler.handle(clonedRoleId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable("id") UUID roleId,
            Authentication authentication
    ) {
        roleAccessEvaluator.assertCanManageRole(authentication, roleId);
        deleteRoleHandler.handle(roleId);
        return ResponseEntity.noContent().build();
    }

    private static RoleDto toDto(RoleView roleView) {
        return new RoleDto(
                roleView.roleId(),
                roleView.moduleId(),
                roleView.name(),
                roleView.description(),
                roleView.createdBy(),
                roleView.createdAt(),
                roleView.permissions().stream()
                        .map(RoleController::toDto)
                        .toList()
        );
    }

    private static RoleDto.RolePermissionDto toDto(RolePermissionView permissionView) {
        return new RoleDto.RolePermissionDto(
                permissionView.permissionId(),
                permissionView.code(),
                permissionView.description(),
                permissionView.status().name()
        );
    }
}
