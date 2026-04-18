package com.platform.authz.sdk.aop;

import com.platform.authz.sdk.annotation.HasPermission;
import com.platform.authz.sdk.cache.RequestScopedPermissionCache;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * Enforces {@link HasPermission} declaratively using request-scoped permissions.
 */
@Aspect
public class HasPermissionAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(HasPermissionAspect.class);
    private static final String ANONYMOUS_USER = "anonymous";

    private final RequestScopedPermissionCache permissionCache;
    private final PermissionMatcher permissionMatcher;

    public HasPermissionAspect(
            RequestScopedPermissionCache permissionCache,
            PermissionMatcher permissionMatcher
    ) {
        this.permissionCache = Objects.requireNonNull(permissionCache, "permissionCache must not be null");
        this.permissionMatcher = Objects.requireNonNull(permissionMatcher, "permissionMatcher must not be null");
    }

    @Around("@annotation(com.platform.authz.sdk.annotation.HasPermission) "
            + "|| @within(com.platform.authz.sdk.annotation.HasPermission)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        HasPermission hasPermission = resolveAnnotation(joinPoint);
        if (hasPermission == null) {
            return joinPoint.proceed();
        }

        String requiredPermission = resolveRequiredPermission(hasPermission);
        Optional<String> userId = resolveUserId();

        if (userId.isEmpty()) {
            logDecision(ANONYMOUS_USER, requiredPermission, false);
            throw new AccessDeniedException("Authentication required");
        }

        Set<String> userPermissions = permissionCache.getPermissions(userId.get());
        boolean allowed = permissionMatcher.matches(userPermissions, requiredPermission);
        logDecision(userId.get(), requiredPermission, allowed);

        if (!allowed) {
            throw new AccessDeniedException("Access denied");
        }

        return joinPoint.proceed();
    }

    private HasPermission resolveAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass()
                : method.getDeclaringClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        HasPermission methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                specificMethod,
                HasPermission.class
        );
        if (methodAnnotation != null) {
            return methodAnnotation;
        }

        return AnnotatedElementUtils.findMergedAnnotation(targetClass, HasPermission.class);
    }

    private String resolveRequiredPermission(HasPermission hasPermission) {
        String requiredPermission = hasPermission.value();
        if (!StringUtils.hasText(requiredPermission)) {
            throw new IllegalStateException("@HasPermission value must not be blank");
        }
        return requiredPermission.trim();
    }

    private Optional<String> resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String userId = authentication.getName();
        if (!StringUtils.hasText(userId) || "anonymousUser".equalsIgnoreCase(userId)) {
            return Optional.empty();
        }

        return Optional.of(userId);
    }

    private void logDecision(String userId, String requiredPermission, boolean allowed) {
        LOGGER.debug(
                "permission_check user={} required={} result={}",
                userId,
                requiredPermission,
                allowed ? "allow" : "deny"
        );
    }
}
