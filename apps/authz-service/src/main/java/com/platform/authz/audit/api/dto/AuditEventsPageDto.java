package com.platform.authz.audit.api.dto;

import java.util.List;

public record AuditEventsPageDto(
        List<AuditEventDto> data,
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
