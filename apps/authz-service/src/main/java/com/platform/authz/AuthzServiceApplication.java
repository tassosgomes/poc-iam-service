package com.platform.authz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.platform.authz.config.AuthzCacheProperties;
import com.platform.authz.config.AuthzSecurityProperties;
import com.platform.authz.iam.infra.CyberArkProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@EnableConfigurationProperties({
        CyberArkProperties.class,
        AuthzCacheProperties.class,
        AuthzSecurityProperties.class
})
@SpringBootApplication
public class AuthzServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthzServiceApplication.class, args);
    }
}
