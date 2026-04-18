package com.platform.authz.sdk.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.platform.authz.sdk.dto.SyncRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads and validates the module permission declaration from YAML.
 */
public class PermissionsYamlLoader {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    private static final ObjectMapper CANONICAL_OBJECT_MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final ResourceLoader resourceLoader;
    private final RegistrationProperties registrationProperties;

    public PermissionsYamlLoader(
            ResourceLoader resourceLoader,
            RegistrationProperties registrationProperties
    ) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        this.registrationProperties = Objects.requireNonNull(
                registrationProperties,
                "registrationProperties must not be null"
        );
    }

    public PermissionsDocument load() {
        Resource resource = resourceLoader.getResource(resolveLocation(registrationProperties.getPermissionsFile()));

        if (!resource.exists()) {
            throw new InvalidPermissionsFileException(
                    "Permissions file not found: " + registrationProperties.getPermissionsFile()
            );
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(inputStream);
            return toPermissionsDocument(loaded);
        } catch (IOException exception) {
            throw new InvalidPermissionsFileException(
                    "Failed to read permissions file: " + registrationProperties.getPermissionsFile(),
                    exception
            );
        }
    }

    public List<SyncRequest.PermissionDeclaration> loadPermissions() {
        return load().permissions();
    }

    public String canonicalize(PermissionsDocument document) {
        Objects.requireNonNull(document, "document must not be null");

        Map<String, Object> root = new TreeMap<>();
        root.put("moduleId", normalizeWhitespace(document.moduleId()));
        root.put("schemaVersion", normalizeWhitespace(document.schemaVersion()));

        List<Map<String, Object>> permissions = document.permissions().stream()
                .map(permission -> {
                    Map<String, Object> permissionMap = new TreeMap<>();
                    permissionMap.put("code", normalizeWhitespace(permission.code()));
                    permissionMap.put("description", normalizeWhitespace(permission.description()));
                    return permissionMap;
                })
                .sorted(Comparator.comparing(permission -> String.valueOf(permission.get("code"))))
                .toList();

        root.put("permissions", permissions);

        try {
            return CANONICAL_OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new InvalidPermissionsFileException("Failed to canonicalize permissions file", exception);
        }
    }

    private PermissionsDocument toPermissionsDocument(Object loaded) {
        if (!(loaded instanceof Map<?, ?> rootMap)) {
            throw new InvalidPermissionsFileException("Permissions file must define a YAML object at the root");
        }

        String schemaVersion = requireNonBlankString(rootMap, "schemaVersion");
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new InvalidPermissionsFileException("Unsupported schemaVersion: " + schemaVersion);
        }

        String moduleId = requireNonBlankString(rootMap, "moduleId");
        List<SyncRequest.PermissionDeclaration> permissions = extractPermissions(rootMap.get("permissions"));

        return new PermissionsDocument(schemaVersion, moduleId, permissions);
    }

    private List<SyncRequest.PermissionDeclaration> extractPermissions(Object rawPermissions) {
        if (!(rawPermissions instanceof List<?> permissionEntries) || permissionEntries.isEmpty()) {
            throw new InvalidPermissionsFileException("permissions must not be empty");
        }

        List<SyncRequest.PermissionDeclaration> permissions = new ArrayList<>();
        for (int index = 0; index < permissionEntries.size(); index++) {
            Object entry = permissionEntries.get(index);
            if (!(entry instanceof Map<?, ?> permissionMap)) {
                throw new InvalidPermissionsFileException(
                        "permissions[" + index + "] must be a YAML object"
                );
            }

            String code = requireNonBlankString(permissionMap, "code", "permissions[" + index + "]");
            String description = requireNonBlankString(permissionMap, "description", "permissions[" + index + "]");
            permissions.add(new SyncRequest.PermissionDeclaration(code, description));
        }

        return List.copyOf(permissions);
    }

    private String requireNonBlankString(Map<?, ?> source, String key) {
        return requireNonBlankString(source, key, "root");
    }

    private String requireNonBlankString(Map<?, ?> source, String key, String scope) {
        Object value = source.get(key);
        if (!(value instanceof String stringValue)) {
            throw new InvalidPermissionsFileException(scope + "." + key + " must be a non-empty string");
        }

        String normalized = normalizeWhitespace(stringValue);
        if (normalized.isBlank()) {
            throw new InvalidPermissionsFileException(scope + "." + key + " must be a non-empty string");
        }

        return normalized;
    }

    private String resolveLocation(String permissionsFile) {
        String location = normalizeWhitespace(permissionsFile);
        if (location.isBlank()) {
            throw new InvalidPermissionsFileException("authz.registration.permissions-file must not be blank");
        }

        if (location.contains(":")) {
            return location;
        }

        return "classpath:" + location;
    }

    static String normalizeWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public record PermissionsDocument(
            String schemaVersion,
            String moduleId,
            List<SyncRequest.PermissionDeclaration> permissions
    ) {

        public PermissionsDocument {
            schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
            moduleId = Objects.requireNonNull(moduleId, "moduleId must not be null");
            permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions must not be null"));
        }
    }

    public static final class InvalidPermissionsFileException extends IllegalStateException {

        public InvalidPermissionsFileException(String message) {
            super(message);
        }

        public InvalidPermissionsFileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
