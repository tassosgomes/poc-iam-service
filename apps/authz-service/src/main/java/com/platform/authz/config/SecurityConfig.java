package com.platform.authz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.authz.audit.application.AuditEventPublisher;
import com.platform.authz.modules.application.ValidateModuleKeyService;
import com.platform.authz.shared.api.ProblemDetailFactory;
import com.platform.authz.shared.api.RequestMetadataResolver;
import com.platform.authz.shared.security.JwtAuthorizationConverter;
import com.platform.authz.shared.security.ModuleBearerAuthenticationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public ModuleBearerAuthenticationFilter moduleBearerAuthenticationFilter(
            ValidateModuleKeyService validateModuleKeyService,
            RequestMetadataResolver requestMetadataResolver,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            MeterRegistry meterRegistry,
            AuditEventPublisher auditEventPublisher,
            Clock clock
    ) {
        return new ModuleBearerAuthenticationFilter(
                validateModuleKeyService,
                requestMetadataResolver,
                handlerExceptionResolver,
                meterRegistry,
                auditEventPublisher,
                clock
        );
    }

    @Bean
    @Order(1)
    public SecurityFilterChain catalogSecurityFilterChain(
            HttpSecurity http,
            ModuleBearerAuthenticationFilter moduleBearerAuthenticationFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .securityMatcher("/v1/catalog/**")
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(moduleBearerAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            JwtAuthorizationConverter jwtAuthorizationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthorizationConverter)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(
            ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(problemDetailFactory, "problemDetailFactory must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");

        return (request, response, exception) -> writeProblemDetail(
                response,
                objectMapper,
                problemDetailFactory.create(
                        HttpStatus.UNAUTHORIZED,
                        "unauthorized",
                        "Unauthorized",
                        "Authentication is required to access this resource",
                        request
                ),
                HttpStatus.UNAUTHORIZED
        );
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(
            ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper
    ) {
        Objects.requireNonNull(problemDetailFactory, "problemDetailFactory must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");

        return (request, response, exception) -> writeProblemDetail(
                response,
                objectMapper,
                problemDetailFactory.create(
                        HttpStatus.FORBIDDEN,
                        "forbidden",
                        "Forbidden",
                        "You do not have permission to access this resource",
                        request
                ),
                HttpStatus.FORBIDDEN
        );
    }

    private void writeProblemDetail(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ProblemDetail problemDetail,
            HttpStatus status
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
