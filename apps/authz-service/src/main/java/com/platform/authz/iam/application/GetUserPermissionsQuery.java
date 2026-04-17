package com.platform.authz.iam.application;

public record GetUserPermissionsQuery(String userId) {

    public GetUserPermissionsQuery {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        userId = userId.trim();
    }
}
