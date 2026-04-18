package com.platform.authz.modules.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.mkammerer.argon2.Argon2Advanced;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class Argon2KeyHasherTest {

    @Test
    void hash_WithRawSecret_ShouldUseConfiguredParametersAndSaltLength() {
        // Arrange
        Argon2Advanced argon2 = Mockito.mock(Argon2Advanced.class);
        Supplier<Argon2Advanced> argon2Supplier = () -> argon2;
        Argon2KeyHasher keyHasher = new Argon2KeyHasher(argon2Supplier);
        byte[] salt = new byte[16];
        String expectedHash = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$hash";

        when(argon2.generateSalt(16)).thenReturn(salt);
        when(argon2.hash(eq(3), eq(65_536), eq(4), any(char[].class), eq(StandardCharsets.UTF_8), eq(salt)))
                .thenReturn(expectedHash);

        // Act
        String result = keyHasher.hash("plain-secret");

        // Assert
        assertThat(result).isEqualTo(expectedHash);
        verify(argon2).generateSalt(16);
        verify(argon2).hash(eq(3), eq(65_536), eq(4), any(char[].class), eq(StandardCharsets.UTF_8), eq(salt));
        verify(argon2).wipeArray(any(char[].class));
        verify(argon2).wipeArray(salt);
    }

    @Test
    void matches_WithStoredHash_ShouldDelegateVerification() {
        // Arrange
        Argon2Advanced argon2 = Mockito.mock(Argon2Advanced.class);
        Supplier<Argon2Advanced> argon2Supplier = () -> argon2;
        Argon2KeyHasher keyHasher = new Argon2KeyHasher(argon2Supplier);

        when(argon2.verify(eq("$argon2id$encoded"), any(char[].class))).thenReturn(true);

        // Act
        boolean result = keyHasher.matches("plain-secret", "$argon2id$encoded");

        // Assert
        assertThat(result).isTrue();
        verify(argon2).verify(eq("$argon2id$encoded"), any(char[].class));
        verify(argon2).wipeArray(any(char[].class));
    }

    @Test
    void hashAndMatches_WithRealArgon2_ShouldRoundTripSuccessfully() {
        // Arrange
        Argon2KeyHasher keyHasher = new Argon2KeyHasher();

        // Act
        String hash = keyHasher.hash("plain-secret");

        // Assert
        assertThat(hash).startsWith("$argon2id$");
        assertThat(keyHasher.matches("plain-secret", hash)).isTrue();
        assertThat(keyHasher.matches("another-secret", hash)).isFalse();
    }
}
