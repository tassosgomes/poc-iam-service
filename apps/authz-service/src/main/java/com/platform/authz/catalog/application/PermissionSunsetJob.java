package com.platform.authz.catalog.application;

import com.platform.authz.catalog.domain.Permission;
import com.platform.authz.catalog.domain.PermissionRepository;
import com.platform.authz.catalog.domain.PermissionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionSunsetJob {
    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionSunsetJob.class);

    private final PermissionRepository permissionRepository;
    private final Clock clock;

    public PermissionSunsetJob(PermissionRepository permissionRepository, Clock clock) {
        this.permissionRepository = Objects.requireNonNull(permissionRepository, "permissionRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Scheduled(cron = "${authz.lifecycle.sunset-cron}")
    @Transactional
    public void run() {
        Instant now = Instant.now(clock);
        List<Permission> duePermissions = permissionRepository.findByStatusAndSunsetAtBefore(
                PermissionStatus.DEPRECATED,
                now
        );
        List<Permission> permissionsToSave = new ArrayList<>();

        for (Permission permission : duePermissions) {
            if (permission.remove(now)) {
                permissionsToSave.add(permission);
            }
        }

        if (!permissionsToSave.isEmpty()) {
            permissionRepository.saveAll(permissionsToSave);
        }

        LOGGER.info(
                "permission_sunset.run due_permissions={} removed_permissions={}",
                duePermissions.size(),
                permissionsToSave.size()
        );
    }
}
