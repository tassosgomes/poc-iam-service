package com.platform.authz.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import org.springframework.stereotype.Component;

@Component
public class RequestMetadataResolver {

    public String resolveActor(Principal principal) {
        return principal != null && principal.getName() != null ? principal.getName() : "unknown";
    }

    public String resolveSourceIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return request.getRemoteAddr();
        }

        return forwardedFor.split(",")[0].trim();
    }
}
