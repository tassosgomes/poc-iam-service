package com.platform.authz.iam.api.dto;

import java.util.List;

public record UserSummaryDto(
        String userId,
        String displayName,
        String email,
        List<String> modules
) {
}
