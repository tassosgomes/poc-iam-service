package com.platform.authz.iam.domain;

import java.util.List;

public record RolePage(List<Role> roles, long totalElements) {

    public RolePage {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
