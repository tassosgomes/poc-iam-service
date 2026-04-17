package com.platform.authz.iam.application;

import com.platform.authz.iam.domain.RolePage;
import com.platform.authz.iam.domain.RoleRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListRolesHandler {

    private final RoleRepository roleRepository;
    private final RoleViewProjector roleViewProjector;

    public ListRolesHandler(
            RoleRepository roleRepository,
            RoleViewProjector roleViewProjector
    ) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.roleViewProjector = Objects.requireNonNull(roleViewProjector, "roleViewProjector must not be null");
    }

    @Transactional(readOnly = true)
    public RoleViewPage handle(ListRolesQuery query) {
        Objects.requireNonNull(query, "query must not be null");

        RolePage rolePage = roleRepository.findPage(query.moduleId(), query.query(), query.page(), query.size());
        return new RoleViewPage(
                roleViewProjector.projectAll(rolePage.roles()),
                query.page(),
                query.size(),
                rolePage.totalElements()
        );
    }
}
