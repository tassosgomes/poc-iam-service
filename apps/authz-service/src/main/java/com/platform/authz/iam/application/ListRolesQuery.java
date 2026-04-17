package com.platform.authz.iam.application;

import java.util.UUID;

public record ListRolesQuery(
        UUID moduleId,
        String query,
        int page,
        int size
) {
}
