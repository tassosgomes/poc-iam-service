package com.platform.authz.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            @Value("${spring.application.name}") String applicationName,
            @Value("${app.environment:dev}") String environment
    ) {
        return registry -> registry.config().commonTags(
                "application", applicationName,
                "environment", environment,
                "service", applicationName
        );
    }
}