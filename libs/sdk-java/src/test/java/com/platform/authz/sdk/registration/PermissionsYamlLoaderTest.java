package com.platform.authz.sdk.registration;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionsYamlLoaderTest {

    @Test
    @DisplayName("load should parse valid YAML and canonicalize deterministically")
    void load_WithValidYaml_ShouldParseAndCanonicalize() {
        // Arrange
        PermissionsYamlLoader loader = new PermissionsYamlLoader(
                new InMemoryResourceLoader(Map.of(
                        "classpath:permissions.yaml",
                        """
                        schemaVersion: "1.0"
                        moduleId: "  vendas  "
                        permissions:
                          - code: "vendas.orders.create"
                            description: "Criar   pedidos\\n de venda"
                          - code: "vendas.orders.cancel"
                            description: " Cancelar pedido em rascunho "
                        """
                )),
                registrationProperties("permissions.yaml")
        );

        // Act
        PermissionsYamlLoader.PermissionsDocument document = loader.load();
        String canonical = loader.canonicalize(document);

        // Assert
        assertThat(document.schemaVersion()).isEqualTo("1.0");
        assertThat(document.moduleId()).isEqualTo("vendas");
        assertThat(document.permissions())
                .extracting(permission -> permission.code() + ":" + permission.description())
                .containsExactly(
                        "vendas.orders.create:Criar pedidos de venda",
                        "vendas.orders.cancel:Cancelar pedido em rascunho"
                );
        assertThat(canonical).isEqualTo(
                """
                {"moduleId":"vendas","permissions":[{"code":"vendas.orders.cancel","description":"Cancelar pedido em rascunho"},{"code":"vendas.orders.create","description":"Criar pedidos de venda"}],"schemaVersion":"1.0"}"""
        );
    }

    @Test
    @DisplayName("load should reject unsupported schema version")
    void load_WithUnsupportedSchemaVersion_ShouldThrowException() {
        // Arrange
        PermissionsYamlLoader loader = new PermissionsYamlLoader(
                new InMemoryResourceLoader(Map.of(
                        "classpath:permissions.yaml",
                        """
                        schemaVersion: "2.0"
                        moduleId: "vendas"
                        permissions:
                          - code: "vendas.orders.create"
                            description: "Criar pedidos"
                        """
                )),
                registrationProperties("permissions.yaml")
        );

        // Act / Assert
        assertThatThrownBy(loader::load)
                .isInstanceOf(PermissionsYamlLoader.InvalidPermissionsFileException.class)
                .hasMessageContaining("Unsupported schemaVersion");
    }

    @Test
    @DisplayName("load should reject permission without description")
    void load_WithMissingPermissionDescription_ShouldThrowException() {
        // Arrange
        PermissionsYamlLoader loader = new PermissionsYamlLoader(
                new InMemoryResourceLoader(Map.of(
                        "classpath:permissions.yaml",
                        """
                        schemaVersion: "1.0"
                        moduleId: "vendas"
                        permissions:
                          - code: "vendas.orders.create"
                        """
                )),
                registrationProperties("permissions.yaml")
        );

        // Act / Assert
        assertThatThrownBy(loader::load)
                .isInstanceOf(PermissionsYamlLoader.InvalidPermissionsFileException.class)
                .hasMessageContaining("permissions[0].description");
    }

    private static RegistrationProperties registrationProperties(String permissionsFile) {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setPermissionsFile(permissionsFile);
        return properties;
    }

    private static final class InMemoryResourceLoader implements ResourceLoader {

        private final Map<String, String> resources;

        private InMemoryResourceLoader(Map<String, String> resources) {
            this.resources = resources;
        }

        @Override
        public Resource getResource(String location) {
            String content = resources.get(location);
            if (content == null) {
                return new ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                        return false;
                    }
                };
            }

            return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getDescription() {
                    return location;
                }
            };
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
