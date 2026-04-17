package com.platform.authz.authz.application;

public record CheckPermissionQuery(String userId, String permission) {

    public CheckPermissionQuery {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("permission must not be blank");
        }
        userId = userId.trim();
        permission = permission.trim();
    }
}
