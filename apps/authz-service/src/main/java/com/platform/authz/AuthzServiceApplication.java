package com.platform.authz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@EnableConfigurationProperties(com.platform.authz.iam.infra.CyberArkProperties.class)
@SpringBootApplication
public class AuthzServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthzServiceApplication.class, args);
    }
}