package com.platform.authz.sdk.aop;

import com.platform.authz.sdk.annotation.HasPermission;
import com.platform.authz.sdk.cache.RequestScopedPermissionCache;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HasPermissionAspectTest {

    @Mock
    private RequestScopedPermissionCache permissionCache;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private HasPermissionAspect hasPermissionAspect;

    @BeforeEach
    void setUp() {
        hasPermissionAspect = new HasPermissionAspect(permissionCache, new PermissionMatcher());
        when(joinPoint.getSignature()).thenReturn(methodSignature);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("authorize should proceed when user has required permission")
    void authorize_WhenUserHasRequiredPermission_ShouldProceed() throws Throwable {
        // Arrange
        Method method = SampleService.class.getMethod("createOrder");
        configureJoinPoint(method, new SampleService());
        authenticate("user-1");
        when(permissionCache.getPermissions("user-1")).thenReturn(Set.of("vendas.orders.create"));
        when(joinPoint.proceed()).thenReturn("allowed");

        // Act
        Object result = hasPermissionAspect.authorize(joinPoint);

        // Assert
        assertThat(result).isEqualTo("allowed");
        verify(permissionCache).getPermissions("user-1");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("authorize should deny when user lacks required permission")
    void authorize_WhenUserLacksRequiredPermission_ShouldDeny() throws Throwable {
        // Arrange
        Method method = SampleService.class.getMethod("createOrder");
        configureJoinPoint(method, new SampleService());
        authenticate("user-1");
        when(permissionCache.getPermissions("user-1")).thenReturn(Set.of("vendas.orders.read"));

        // Act / Assert
        assertThatThrownBy(() -> hasPermissionAspect.authorize(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied");

        verify(permissionCache).getPermissions("user-1");
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("authorize should allow wildcard user permission")
    void authorize_WhenUserHasWildcardPermission_ShouldProceed() throws Throwable {
        // Arrange
        Method method = SampleService.class.getMethod("createOrder");
        configureJoinPoint(method, new SampleService());
        authenticate("user-1");
        when(permissionCache.getPermissions("user-1")).thenReturn(Set.of("vendas.*"));
        when(joinPoint.proceed()).thenReturn("allowed");

        // Act
        Object result = hasPermissionAspect.authorize(joinPoint);

        // Assert
        assertThat(result).isEqualTo("allowed");
        verify(permissionCache).getPermissions("user-1");
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("authorize should deny by default when authentication is missing")
    void authorize_WithoutAuthentication_ShouldDeny() throws Throwable {
        // Arrange
        Method method = SampleService.class.getMethod("createOrder");
        configureJoinPoint(method, new SampleService());

        // Act / Assert
        assertThatThrownBy(() -> hasPermissionAspect.authorize(joinPoint))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Authentication required");

        verify(permissionCache, never()).getPermissions("user-1");
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("authorize should resolve permission declared at type level")
    void authorize_WhenPermissionIsDeclaredAtTypeLevel_ShouldProceed() throws Throwable {
        // Arrange
        Method method = TypeAnnotatedService.class.getMethod("listOrders");
        configureJoinPoint(method, new TypeAnnotatedService());
        authenticate("user-1");
        when(permissionCache.getPermissions("user-1")).thenReturn(Set.of("vendas.orders.read"));
        when(joinPoint.proceed()).thenReturn(List.of("order-1"));

        // Act
        Object result = hasPermissionAspect.authorize(joinPoint);

        // Assert
        assertThat(result).isEqualTo(List.of("order-1"));
        verify(permissionCache).getPermissions("user-1");
        verify(joinPoint).proceed();
    }

    private void configureJoinPoint(Method method, Object target) {
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(target);
    }

    private void authenticate(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, "n/a", List.of())
        );
    }

    static class SampleService {

        @HasPermission("vendas.orders.create")
        public String createOrder() {
            return "allowed";
        }
    }

    @HasPermission("vendas.orders.read")
    static class TypeAnnotatedService {

        public List<String> listOrders() {
            return List.of("order-1");
        }
    }
}
