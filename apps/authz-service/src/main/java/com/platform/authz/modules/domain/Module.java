package com.platform.authz.modules.domain;

import com.platform.authz.shared.domain.ValidationPatterns;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Module(
        UUID id,
        String name,
        String allowedPrefix,
        String description,
        String createdBy,
        Instant createdAt,
        Instant lastHeartbeatAt
) {
    private static final Pattern ALLOWED_PREFIX_PATTERN = Pattern.compile(ValidationPatterns.ALLOWED_PREFIX_REGEX);

    public Module {
        Objects.requireNonNull(id, "id must not be null");
        name = requireText(name, "name");
        allowedPrefix = requireAllowedPrefix(allowedPrefix);
        description = requireText(description, "description");
        createdBy = requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Module create(
            String name,
            String allowedPrefix,
            String description,
            String createdBy,
            Instant createdAt
    ) {
        return new Module(
                UUID.randomUUID(),
                name,
                allowedPrefix,
                description,
                createdBy,
                createdAt,
                null
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("%s must not be blank".formatted(fieldName));
        }

        return value.trim();
    }

    private static String requireAllowedPrefix(String allowedPrefix) {
        String normalized = requireText(allowedPrefix, "allowedPrefix");

        if (!normalized.equals(normalized.toLowerCase(Locale.ROOT))
                || !ALLOWED_PREFIX_PATTERN.matcher(normalized).matches()) {
            throw new InvalidAllowedPrefixException(allowedPrefix);
        }

        return normalized;
    }
}
