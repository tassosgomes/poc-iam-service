package com.platform.authz.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.authz.modules.application.ValidateModuleKeyService;
import com.platform.authz.shared.api.RequestMetadataResolver;
import com.platform.authz.shared.exception.UnauthorizedModuleKeyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

@ExtendWith(MockitoExtension.class)
class ModuleBearerAuthenticationFilterTest {

    @Mock
    private ValidateModuleKeyService validateModuleKeyService;

    @Mock
    private RequestMetadataResolver requestMetadataResolver;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    private SimpleMeterRegistry meterRegistry;
    private ModuleBearerAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new ModuleBearerAuthenticationFilter(
                validateModuleKeyService,
                requestMetadataResolver,
                handlerExceptionResolver,
                meterRegistry
        );
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_WithValidHeaders_ShouldPopulateSecurityContext() throws Exception {
        // Arrange
        String moduleId = "98ec60dc-bf84-4540-b863-da1452682f8b";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/catalog/sync");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-secret");
        request.addHeader("X-Module-Id", moduleId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ModuleContext moduleContext = new ModuleContext(moduleId, "sales", Instant.parse("2026-04-16T10:00:00Z"));
        when(validateModuleKeyService.validate(moduleId, "valid-secret")).thenReturn(moduleContext);
        AtomicReference<Authentication> authenticationReference = new AtomicReference<>();

        // Act
        filter.doFilter(
                request,
                response,
                new CapturingFilterChain(authenticationReference)
        );

        // Assert
        assertThat(authenticationReference.get()).isInstanceOf(ModuleAuthenticationToken.class);
        assertThat(authenticationReference.get().getPrincipal()).isEqualTo(moduleContext);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilterInternal_WithoutAuthorizationHeader_ShouldResolveUnauthorizedException() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/catalog/sync");
        request.addHeader("X-Module-Id", "98ec60dc-bf84-4540-b863-da1452682f8b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(requestMetadataResolver.resolveSourceIp(request)).thenReturn("10.0.0.10");

        // Act
        filter.doFilter(request, response, new NoOpFilterChain());

        // Assert
        verify(handlerExceptionResolver).resolveException(
                any(),
                any(),
                isNull(),
                any(UnauthorizedModuleKeyException.class)
        );
        assertThat(meterRegistry.get("authz_module_key_invalid_total")
                .tag("reason", "not_found")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void doFilterInternal_WithUnsupportedAuthorizationScheme_ShouldResolveUnauthorizedException() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/catalog/sync");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123");
        request.addHeader("X-Module-Id", "98ec60dc-bf84-4540-b863-da1452682f8b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(requestMetadataResolver.resolveSourceIp(request)).thenReturn("10.0.0.13");

        // Act
        filter.doFilter(request, response, new NoOpFilterChain());

        // Assert
        verify(handlerExceptionResolver).resolveException(
                any(),
                any(),
                isNull(),
                any(UnauthorizedModuleKeyException.class)
        );
        verify(validateModuleKeyService, org.mockito.Mockito.never()).validate(any(), any());
        assertThat(meterRegistry.get("authz_module_key_invalid_total")
                .tag("reason", "not_found")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void doFilterInternal_WithoutModuleIdHeader_ShouldResolveUnauthorizedException() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/catalog/sync");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(validateModuleKeyService.validate(null, "valid-secret"))
                .thenThrow(new UnauthorizedModuleKeyException("missing_module_id"));
        when(requestMetadataResolver.resolveSourceIp(request)).thenReturn("10.0.0.11");

        // Act
        filter.doFilter(request, response, new NoOpFilterChain());

        // Assert
        verify(handlerExceptionResolver).resolveException(
                any(),
                any(),
                isNull(),
                any(UnauthorizedModuleKeyException.class)
        );
        assertThat(meterRegistry.get("authz_module_key_invalid_total")
                .tag("reason", "not_found")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void doFilterInternal_WithEmptyBearerToken_ShouldResolveUnauthorizedException() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/catalog/sync");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer   ");
        request.addHeader("X-Module-Id", "98ec60dc-bf84-4540-b863-da1452682f8b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(requestMetadataResolver.resolveSourceIp(request)).thenReturn("10.0.0.12");

        // Act
        filter.doFilter(request, response, new NoOpFilterChain());

        // Assert
        verify(handlerExceptionResolver).resolveException(
                any(),
                any(),
                isNull(),
                any(UnauthorizedModuleKeyException.class)
        );
        verify(validateModuleKeyService, org.mockito.Mockito.never()).validate(any(), any());
        assertThat(meterRegistry.get("authz_module_key_invalid_total")
                .tag("reason", "not_found")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    private static final class CapturingFilterChain implements FilterChain {
        private final AtomicReference<Authentication> authenticationReference;

        private CapturingFilterChain(AtomicReference<Authentication> authenticationReference) {
            this.authenticationReference = authenticationReference;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
            authenticationReference.set(SecurityContextHolder.getContext().getAuthentication());
            ((MockHttpServletResponse) response).setStatus(200);
        }
    }

    private static final class NoOpFilterChain implements FilterChain {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            throw new AssertionError("Filter chain should not be invoked");
        }
    }
}
