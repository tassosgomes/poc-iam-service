package com.platform.authz.iam.application;

import java.util.List;

public record RoleViewPage(
        List<RoleView> roles,
        int page,
        int size,
        long totalElements
) {
}
