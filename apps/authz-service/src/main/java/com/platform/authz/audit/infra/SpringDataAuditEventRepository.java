package com.platform.authz.audit.infra;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAuditEventRepository extends JpaRepository<AuditEventEntity, UUID> {
}
