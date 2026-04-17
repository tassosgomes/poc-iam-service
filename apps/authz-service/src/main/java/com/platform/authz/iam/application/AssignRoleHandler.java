package com.platform.authz.iam.application;

import com.platform.authz.audit.application.AuditEventPublisher;
import com.platform.authz.audit.domain.AuditEvent;
import com.platform.authz.audit.domain.AuditEventType;
import com.platform.authz.iam.domain.Role;
import com.platform.authz.iam.domain.RoleNotFoundException;
import com.platform.authz.iam.domain.RoleRepository;
import com.platform.authz.iam.domain.UserNotFoundException;
import com.platform.authz.iam.domain.UserRoleAssignment;
import com.platform.authz.iam.domain.UserRoleAssignmentConflictException;
import com.platform.authz.iam.domain.UserRoleRepository;
import com.platform.authz.modules.domain.Module;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import com.platform.authz.modules.domain.ModuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignRoleHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssignRoleHandler.class);

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserSearchPort userSearchPort;
    private final AdminScopeChecker adminScopeChecker;
    private final ModuleRepository moduleRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public AssignRoleHandler(
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            UserSearchPort userSearchPort,
            AdminScopeChecker adminScopeChecker,
            ModuleRepository moduleRepository,
            AuditEventPublisher auditEventPublisher,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.userRoleRepository = Objects.requireNonNull(userRoleRepository, "userRoleRepository must not be null");
        this.userSearchPort = Objects.requireNonNull(userSearchPort, "userSearchPort must not be null");
        this.adminScopeChecker = Objects.requireNonNull(adminScopeChecker, "adminScopeChecker must not be null");
        this.moduleRepository = Objects.requireNonNull(moduleRepository, "moduleRepository must not be null");
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher, "auditEventPublisher must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AssignRoleResult handle(AssignRoleCommand command, Authentication authentication) {
        Objects.requireNonNull(command, "command must not be null");

        Role role = roleRepository.findById(command.roleId())
                .orElseThrow(() -> new RoleNotFoundException(command.roleId()));
        Module module = moduleRepository.findById(role.moduleId())
                .orElseThrow(() -> new ModuleNotFoundException(role.moduleId()));

        adminScopeChecker.requireScope(authentication, role.moduleId());

        if (!userSearchPort.userExists(command.userId())) {
            recordMetric("assign", module.allowedPrefix(), "user_not_found");
            throw new UserNotFoundException(command.userId());
        }

        var activeAssignment = userRoleRepository.findActiveByUserIdAndRoleId(command.userId(), command.roleId());
        if (activeAssignment.isPresent()) {
            recordMetric("assign", module.allowedPrefix(), "idempotent");
            return new AssignRoleResult(false, toView(activeAssignment.get(), role));
        }

        Instant now = Instant.now(clock);
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                command.userId(),
                command.roleId(),
                command.assignedBy(),
                now
        );
        UserRoleAssignment savedAssignment;
        try {
            savedAssignment = userRoleRepository.save(assignment);
        } catch (DataIntegrityViolationException exception) {
            var concurrentAssignment = userRoleRepository.findActiveByUserIdAndRoleId(command.userId(), command.roleId());
            if (concurrentAssignment.isPresent()) {
                recordMetric("assign", module.allowedPrefix(), "idempotent");
                LOGGER.info(
                        "role_assignment_idempotent actor={} target={} role={}",
                        command.assignedBy(),
                        command.userId(),
                        role.name()
                );
                return new AssignRoleResult(false, toView(concurrentAssignment.get(), role));
            }

            recordMetric("assign", module.allowedPrefix(), "conflict");
            throw new UserRoleAssignmentConflictException(command.userId(), role.name());
        }

        auditEventPublisher.publish(new AuditEvent(
                java.util.UUID.randomUUID(),
                AuditEventType.ROLE_ASSIGNED,
                command.assignedBy(),
                command.userId(),
                Map.of(
                        "userId", command.userId(),
                        "roleId", role.id().toString(),
                        "roleName", role.name(),
                        "moduleId", role.moduleId().toString(),
                        "assignedAt", savedAssignment.assignedAt().toString()
                ),
                normalizeSourceIp(command.sourceIp()),
                savedAssignment.assignedAt()
        ));

        LOGGER.info("role_assigned actor={} target={} role={}", command.assignedBy(), command.userId(), role.name());
        recordMetric("assign", module.allowedPrefix(), "created");
        return new AssignRoleResult(true, toView(savedAssignment, role));
    }

    private UserRoleView toView(UserRoleAssignment assignment, Role role) {
        return new UserRoleView(
                assignment.userId(),
                role.id(),
                role.moduleId(),
                role.name(),
                assignment.assignedBy(),
                assignment.assignedAt()
        );
    }

    private void recordMetric(String action, String module, String result) {
        meterRegistry.counter(
                "authz_role_assignment_total",
                "action",
                action,
                "module",
                module,
                "result",
                result
        ).increment();
    }

    private String normalizeSourceIp(String sourceIp) {
        return sourceIp == null || sourceIp.isBlank() ? null : sourceIp;
    }
}
