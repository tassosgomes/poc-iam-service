package com.platform.authz.shared.security;

import com.platform.authz.modules.application.ValidateModuleKeyService;
import com.platform.authz.shared.api.RequestMetadataResolver;
import com.platform.authz.shared.exception.UnauthorizedModuleKeyException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

public class ModuleBearerAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleBearerAuthenticationFilter.class);
    private static final String MODULE_ID_HEADER = "X-Module-Id";
    private static final String AUTHORIZATION_PREFIX = "Bearer ";

    private final ValidateModuleKeyService validateModuleKeyService;
    private final RequestMetadataResolver requestMetadataResolver;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final MeterRegistry meterRegistry;

    public ModuleBearerAuthenticationFilter(
            ValidateModuleKeyService validateModuleKeyService,
            RequestMetadataResolver requestMetadataResolver,
            HandlerExceptionResolver handlerExceptionResolver,
            MeterRegistry meterRegistry
    ) {
        this.validateModuleKeyService = Objects.requireNonNull(
                validateModuleKeyService,
                "validateModuleKeyService must not be null"
        );
        this.requestMetadataResolver = Objects.requireNonNull(
                requestMetadataResolver,
                "requestMetadataResolver must not be null"
        );
        this.handlerExceptionResolver = Objects.requireNonNull(
                handlerExceptionResolver,
                "handlerExceptionResolver must not be null"
        );
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !requestUri.equals("/v1/catalog") && !requestUri.startsWith("/v1/catalog/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            ModuleContext moduleContext = authenticate(request);
            ModuleAuthenticationToken authentication = new ModuleAuthenticationToken(moduleContext);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedModuleKeyException exception) {
            SecurityContextHolder.clearContext();
            recordFailure(exception.reason(), request);
            handlerExceptionResolver.resolveException(request, response, null, exception);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            handlerExceptionResolver.resolveException(request, response, null, exception);
        }
    }

    private ModuleContext authenticate(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || authorizationHeader.isBlank() || !authorizationHeader.startsWith(AUTHORIZATION_PREFIX)) {
            throw new UnauthorizedModuleKeyException("malformed_header");
        }

        String secret = authorizationHeader.substring(AUTHORIZATION_PREFIX.length()).trim();
        if (secret.isEmpty()) {
            throw new UnauthorizedModuleKeyException("malformed_header");
        }

        return validateModuleKeyService.validate(request.getHeader(MODULE_ID_HEADER), secret);
    }

    private void recordFailure(String reason, HttpServletRequest request) {
        String metricReason = switch (reason) {
            case "expired_grace", "revoked" -> reason;
            default -> "not_found";
        };

        meterRegistry.counter("authz_module_key_invalid_total", "reason", metricReason).increment();
        LOGGER.warn(
                "key_auth_failed source_ip={} reason={}",
                requestMetadataResolver.resolveSourceIp(request),
                reason
        );
    }
}
