package com.platform.authz.audit.infra;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

    @Query(
            value = """
                    SELECT *
                    FROM audit_event ae
                    WHERE (CAST(:eventType AS text) IS NULL OR ae.event_type = CAST(:eventType AS text))
                      AND (CAST(:moduleId AS text) IS NULL OR ae.payload ->> 'moduleId' = CAST(:moduleId AS text))
                      AND (CAST(:actorId AS text) IS NULL OR ae.actor_id = CAST(:actorId AS text))
                      AND (CAST(:fromOccurredAt AS timestamptz) IS NULL OR ae.occurred_at >= CAST(:fromOccurredAt AS timestamptz))
                      AND (CAST(:toOccurredAt AS timestamptz) IS NULL OR ae.occurred_at <= CAST(:toOccurredAt AS timestamptz))
                    ORDER BY ae.occurred_at DESC, ae.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM audit_event ae
                    WHERE (CAST(:eventType AS text) IS NULL OR ae.event_type = CAST(:eventType AS text))
                      AND (CAST(:moduleId AS text) IS NULL OR ae.payload ->> 'moduleId' = CAST(:moduleId AS text))
                      AND (CAST(:actorId AS text) IS NULL OR ae.actor_id = CAST(:actorId AS text))
                      AND (CAST(:fromOccurredAt AS timestamptz) IS NULL OR ae.occurred_at >= CAST(:fromOccurredAt AS timestamptz))
                      AND (CAST(:toOccurredAt AS timestamptz) IS NULL OR ae.occurred_at <= CAST(:toOccurredAt AS timestamptz))
                    """,
            nativeQuery = true
    )
    Page<AuditEventJpaEntity> search(
            @Param("eventType") String eventType,
            @Param("moduleId") String moduleId,
            @Param("actorId") String actorId,
            @Param("fromOccurredAt") Instant fromOccurredAt,
            @Param("toOccurredAt") Instant toOccurredAt,
            Pageable pageable
    );
}
