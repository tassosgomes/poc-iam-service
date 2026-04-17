package com.platform.authz.modules.application;

public record CreateModuleCommand(
        String name,
        String allowedPrefix,
        String description,
        String createdBy,
        String sourceIp
) {
}
