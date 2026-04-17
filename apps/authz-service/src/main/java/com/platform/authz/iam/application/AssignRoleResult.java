package com.platform.authz.iam.application;

public record AssignRoleResult(
        boolean created,
        UserRoleView assignment
) {
}
