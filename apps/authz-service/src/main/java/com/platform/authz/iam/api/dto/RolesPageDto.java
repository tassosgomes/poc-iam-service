package com.platform.authz.iam.api.dto;

import java.util.List;

public record RolesPageDto(
        List<RoleDto> data,
        PaginationDto pagination
) {
    public record PaginationDto(
            int page,
            int size,
            long total,
            long totalPages
    ) {
    }
}
