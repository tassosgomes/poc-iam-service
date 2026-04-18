package com.platform.authz.sdk.exception;

/**
 * Exception thrown by the AuthZ SDK when a remote call fails.
 * Wraps HTTP errors, timeouts, and circuit-breaker open states.
 */
public class AuthzClientException extends RuntimeException {

    private final int statusCode;

    public AuthzClientException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public AuthzClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public AuthzClientException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AuthzClientException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }
}
