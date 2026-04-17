package com.platform.authz.shared.api;

import com.platform.authz.iam.domain.AdminScopeViolationException;
import com.platform.authz.iam.domain.InvalidRoleNameException;
import com.platform.authz.iam.domain.InvalidRolePermissionStatusException;
import com.platform.authz.iam.domain.PermissionModuleMismatchException;
import com.platform.authz.iam.domain.RoleConflictException;
import com.platform.authz.iam.domain.RoleDeletionConflictException;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.UserNotFoundException;
import com.platform.authz.iam.domain.UserRoleAssignmentConflictException;
import com.platform.authz.iam.domain.UserSearchAccessDeniedException;
import com.platform.authz.iam.infra.CyberArkUnavailableException;
import com.platform.authz.modules.api.PlatformAdminRequiredException;
import com.platform.authz.modules.domain.InvalidAllowedPrefixException;
import com.platform.authz.modules.domain.ModuleActiveKeyNotFoundException;
import com.platform.authz.modules.domain.ModuleAlreadyExistsException;
import com.platform.authz.modules.domain.ModuleConflictException;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.shared.exception.InvalidModuleAuthenticationException;
import com.platform.authz.shared.exception.ModuleIdMismatchException;
import com.platform.authz.shared.exception.PrefixViolationException;
import com.platform.authz.shared.exception.UnauthorizedModuleKeyException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = Objects.requireNonNull(problemDetailFactory, "problemDetailFactory must not be null");
    }

    @ExceptionHandler({ModuleAlreadyExistsException.class, ModuleConflictException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception, HttpServletRequest request) {
        return problemDetailFactory.buildResponse(
                HttpStatus.CONFLICT,
                "module-conflict",
                "Module conflict",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            RoleConflictException.class,
            RoleDeletionConflictException.class,
            UserRoleAssignmentConflictException.class
    })
    public ResponseEntity<ProblemDetail> handleRoleConflict(RuntimeException exception, HttpServletRequest request) {
        return problemDetailFactory.buildResponse(
                HttpStatus.CONFLICT,
                "role-conflict",
                "Role conflict",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler({
            ModuleNotFoundException.class,
            RoleNotFoundException.class,
            UserNotFoundException.class,
            EntityNotFoundException.class
    })
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException exception, HttpServletRequest request) {
        String type = exception instanceof UserNotFoundException ? "user-not-found" : "resource-not-found";
        return problemDetailFactory.buildResponse(HttpStatus.NOT_FOUND, type, "Not Found", exception.getMessage(), request);
    }

    @ExceptionHandler({
            InvalidAllowedPrefixException.class,
            ModuleActiveKeyNotFoundException.class,
            InvalidRoleNameException.class,
            PermissionModuleMismatchException.class,
            InvalidRolePermissionStatusException.class
    })
    public ResponseEntity<ProblemDetail> handleUnprocessable(RuntimeException exception, HttpServletRequest request) {
        ProblemDetail problemDetail = problemDetailFactory.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                resolveUnprocessableType(exception),
                "Request rejected",
                exception.getMessage(),
                request
        );
        if (exception instanceof PermissionModuleMismatchException) {
            problemDetail.setProperty("errorCode", "permission_module_mismatch");
        }

        return ResponseEntity.unprocessableEntity()
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(PlatformAdminRequiredException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(PlatformAdminRequiredException exception, HttpServletRequest request) {
        return problemDetailFactory.buildResponse(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(AdminScopeViolationException.class)
    public ResponseEntity<ProblemDetail> handleAdminScopeViolation(
            AdminScopeViolationException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = problemDetailFactory.create(
                HttpStatus.FORBIDDEN,
                "admin-scope-violation",
                "Forbidden",
                exception.getMessage(),
                request
        );
        problemDetail.setProperty("errorCode", "admin_scope_violation");

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(UnauthorizedModuleKeyException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorizedModuleKey(
            UnauthorizedModuleKeyException exception,
            HttpServletRequest request
    ) {
        return problemDetailFactory.buildResponse(
                HttpStatus.UNAUTHORIZED,
                "unauthorized-module-key",
                "Unauthorized",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(InvalidModuleAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidModuleAuthentication(
            InvalidModuleAuthenticationException exception,
            HttpServletRequest request
    ) {
        return problemDetailFactory.buildResponse(
                HttpStatus.UNAUTHORIZED,
                "invalid-module-authentication",
                "Unauthorized",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ModuleIdMismatchException.class)
    public ResponseEntity<ProblemDetail> handleModuleIdMismatch(
            ModuleIdMismatchException exception,
            HttpServletRequest request
    ) {
        return problemDetailFactory.buildResponse(
                HttpStatus.FORBIDDEN,
                "module-id-mismatch",
                "Forbidden",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(PrefixViolationException.class)
    public ResponseEntity<ProblemDetail> handlePrefixViolation(PrefixViolationException exception, HttpServletRequest request) {
        return problemDetailFactory.buildResponse(
                HttpStatus.FORBIDDEN,
                "permission-prefix-violation",
                "Forbidden",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = problemDetailFactory.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-error",
                "Validation error",
                "Request validation failed",
                request
        );
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );
        problemDetail.setProperty("errors", errors);

        return ResponseEntity.unprocessableEntity()
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = problemDetailFactory.create(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "validation-error",
                "Validation error",
                "Request validation failed",
                request
        );
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                errors.put(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Invalid value"
                )
        );
        problemDetail.setProperty("errors", errors);

        return ResponseEntity.unprocessableEntity()
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(CyberArkUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleCyberArkUnavailable(
            CyberArkUnavailableException exception,
            HttpServletRequest request
    ) {
        LOGGER.warn("cyberark_unavailable path={}", request != null ? request.getRequestURI() : "unknown");
        return problemDetailFactory.buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "cyberark-unavailable",
                "Service Unavailable",
                "Identity provider is temporarily unavailable. Please try again later.",
                request
        );
    }

    @ExceptionHandler(UserSearchAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleUserSearchAccessDenied(
            UserSearchAccessDeniedException exception,
            HttpServletRequest request
    ) {
        return problemDetailFactory.buildResponse(
                HttpStatus.FORBIDDEN,
                "user-search-forbidden",
                "Forbidden",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("unexpected_error path={}", request != null ? request.getRequestURI() : "unknown", exception);
        return problemDetailFactory.buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-server-error",
                "Internal Server Error",
                "An unexpected error occurred",
                request
        );
    }

    private String resolveUnprocessableType(RuntimeException exception) {
        if (exception instanceof PermissionModuleMismatchException) {
            return "permission-module-mismatch";
        }
        if (exception instanceof InvalidRoleNameException) {
            return "invalid-role-name";
        }
        if (exception instanceof InvalidRolePermissionStatusException) {
            return "invalid-role-permission-status";
        }
        return "module-request-rejected";
    }
}
