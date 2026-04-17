package com.platform.authz.modules.api;

public class PlatformAdminRequiredException extends RuntimeException {

    public PlatformAdminRequiredException() {
        super("Platform administrator role is required");
    }
}
