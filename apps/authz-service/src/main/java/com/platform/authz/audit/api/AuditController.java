package com.platform.authz.audit.api;

import com.platform.authz.audit.api.dto.AuditEventDto;
import com.platform.authz.audit.api.dto.AuditEventQueryParams;
import com.platform.authz.audit.api.dto.AuditEventsPageDto;
import com.platform.authz.audit.application.ListAuditEventsHandler;
import com.platform.authz.audit.application.ListAuditEventsQuery;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventPage;
import com.platform.authz.audit.domain.AuditEventType;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/audit/events")
public class AuditController {

    private final ListAuditEventsHandler listAuditEventsHandler;

    public AuditController(ListAuditEventsHandler listAuditEventsHandler) {
        this.listAuditEventsHandler = Objects.requireNonNull(
                listAuditEventsHandler,
                "listAuditEventsHandler must not be null"
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'AUDITOR')")
    public AuditEventsPageDto listEvents(@Valid @ModelAttribute AuditEventQueryParams queryParams) {
        int page = queryParams.page() != null ? queryParams.page() : 1;
        int size = queryParams.size() != null ? queryParams.size() : 20;

        AuditEventPage result = listAuditEventsHandler.handle(new ListAuditEventsQuery(
                parseEventType(queryParams.eventType()),
                queryParams.moduleId(),
                normalize(queryParams.actorId()),
                queryParams.from(),
                queryParams.to(),
                page,
                size
        ));
        long totalPages = result.totalElements() == 0 ? 0 : (long) Math.ceil((double) result.totalElements() / size);

        return new AuditEventsPageDto(
                result.events().stream().map(AuditController::toDto).toList(),
                new AuditEventsPageDto.PaginationDto(page, size, result.totalElements(), totalPages)
        );
    }

    private static AuditEventDto toDto(AuditEvent auditEvent) {
        return new AuditEventDto(
                auditEvent.id(),
                auditEvent.eventType().name(),
                auditEvent.actorId(),
                auditEvent.target(),
                auditEvent.payload(),
                auditEvent.sourceIp(),
                auditEvent.occurredAt()
        );
    }

    private AuditEventType parseEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }

        return AuditEventType.valueOf(eventType.trim().toUpperCase(Locale.ROOT));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
