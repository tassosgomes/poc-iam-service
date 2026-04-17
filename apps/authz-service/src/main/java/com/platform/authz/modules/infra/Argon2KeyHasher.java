package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.ModuleKeyHasher;
import de.mkammerer.argon2.Argon2Advanced;
import de.mkammerer.argon2.Argon2Factory;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class Argon2KeyHasher implements ModuleKeyHasher {
    private static final int ITERATIONS = 3;
    private static final int MEMORY_IN_KIB = 65_536;
    private static final int PARALLELISM = 4;
    private static final int SALT_LENGTH = 16;

    private final Supplier<Argon2Advanced> argon2Supplier;

    public Argon2KeyHasher() {
        this(() -> Argon2Factory.createAdvanced(Argon2Factory.Argon2Types.ARGON2id));
    }

    Argon2KeyHasher(Supplier<Argon2Advanced> argon2Supplier) {
        this.argon2Supplier = Objects.requireNonNull(argon2Supplier, "argon2Supplier must not be null");
    }

    @Override
    public String hash(String rawSecret) {
        Objects.requireNonNull(rawSecret, "rawSecret must not be null");

        Argon2Advanced argon2 = argon2Supplier.get();
        char[] secretChars = rawSecret.toCharArray();
        byte[] salt = argon2.generateSalt(SALT_LENGTH);

        try {
            return argon2.hash(
                    ITERATIONS,
                    MEMORY_IN_KIB,
                    PARALLELISM,
                    secretChars,
                    StandardCharsets.UTF_8,
                    salt
            );
        } finally {
            argon2.wipeArray(secretChars);
            argon2.wipeArray(salt);
        }
    }

    @Override
    public boolean matches(String rawSecret, String storedHash) {
        Objects.requireNonNull(rawSecret, "rawSecret must not be null");
        Objects.requireNonNull(storedHash, "storedHash must not be null");

        Argon2Advanced argon2 = argon2Supplier.get();
        char[] secretChars = rawSecret.toCharArray();

        try {
            return argon2.verify(storedHash, secretChars);
        } finally {
            argon2.wipeArray(secretChars);
        }
    }
}
