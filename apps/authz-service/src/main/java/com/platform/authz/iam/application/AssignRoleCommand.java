package com.platform.authz.iam.application;

import java.util.UUID;

public record AssignRoleCommand(
        String userId,
        UUID roleId,
        String assignedBy,
        String sourceIp
) {
}
