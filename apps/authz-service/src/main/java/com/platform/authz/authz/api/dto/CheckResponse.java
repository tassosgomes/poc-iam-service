package com.platform.authz.authz.api.dto;

import java.util.Objects;

public record CheckResponse(boolean allowed, String source) {

    public CheckResponse {
        Objects.requireNonNull(source, "source must not be null");
    }
}
