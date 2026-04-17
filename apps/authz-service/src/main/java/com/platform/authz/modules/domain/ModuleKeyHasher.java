package com.platform.authz.modules.domain;

public interface ModuleKeyHasher {

    String hash(String rawSecret);

    boolean matches(String rawSecret, String storedHash);
}
