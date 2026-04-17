package com.platform.authz.iam.infra;

public class CyberArkUnavailableException extends RuntimeException {

    public CyberArkUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
