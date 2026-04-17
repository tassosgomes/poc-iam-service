package com.platform.authz.authz.application;

import java.util.Objects;

public record CheckPermissionResult(boolean allowed, String source) {

    public static final String SOURCE_ACTIVE = "active";
    public static final String SOURCE_DEPRECATED = "deprecated";
    public static final String SOURCE_DENIED = "denied";

    public CheckPermissionResult {
        Objects.requireNonNull(source, "source must not be null");
        if (!SOURCE_ACTIVE.equals(source) && !SOURCE_DEPRECATED.equals(source) && !SOURCE_DENIED.equals(source)) {
            throw new IllegalArgumentException("source must be one of: active, deprecated, denied");
        }
    }

    public static CheckPermissionResult active() {
        return new CheckPermissionResult(true, SOURCE_ACTIVE);
    }

    public static CheckPermissionResult deprecated() {
        return new CheckPermissionResult(true, SOURCE_DEPRECATED);
    }

    public static CheckPermissionResult denied() {
        return new CheckPermissionResult(false, SOURCE_DENIED);
    }
}
