package com.platform.authz.iam.application;

import java.util.UUID;

public record RevokeRoleCommand(
        String userId,
        UUID roleId,
        String revokedBy,
        String sourceIp
) {
}
