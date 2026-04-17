package com.platform.authz.iam.application;

import java.util.List;

public record UserSummary(
        String userId,
        String displayName,
        String email,
        List<String> modules
) {
}
