package com.platform.authz.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authzOpenApi() {
        SecurityScheme moduleBearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("Module Key")
                .description("Bearer module key used by trusted platform modules.");

        SecurityScheme jwtScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("CyberArk-issued JWT used by platform administrators and users.");

        return new OpenAPI()
                .info(new Info()
                        .title("AuthZ Service API")
                        .version("v1")
                        .description("Core authorization platform APIs."))
                .components(new Components()
                        .addSecuritySchemes("moduleBearer", moduleBearerScheme)
                        .addSecuritySchemes("jwtBearer", jwtScheme))
                .addSecurityItem(new SecurityRequirement().addList("jwtBearer"))
                .addSecurityItem(new SecurityRequirement().addList("moduleBearer"));
    }
}