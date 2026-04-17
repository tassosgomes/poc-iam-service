package com.platform.authz.iam.application;

import java.util.UUID;

public record CloneRoleCommand(
        UUID roleId,
        String name,
        String createdBy
) {
}
