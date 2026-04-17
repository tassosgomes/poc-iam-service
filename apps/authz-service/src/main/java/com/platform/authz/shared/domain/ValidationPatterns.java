package com.platform.authz.shared.domain;

public final class ValidationPatterns {

    public static final String ALLOWED_PREFIX_SEGMENT_REGEX = "[a-z][a-z0-9-]{1,30}";
    public static final String ALLOWED_PREFIX_REGEX = "^" + ALLOWED_PREFIX_SEGMENT_REGEX + "$";
    public static final String PERMISSION_CODE_REGEX =
            "^" + ALLOWED_PREFIX_SEGMENT_REGEX + "(\\.[a-z][a-z0-9_]{0,30}){2,}$";

    private ValidationPatterns() {
    }
}
