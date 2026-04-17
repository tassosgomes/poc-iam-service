package com.platform.authz.catalog.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionDeclarationValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validate_WithHyphenatedAllowedPrefixSegment_ShouldAcceptPermissionCode() {
        // Arrange
        PermissionDeclaration declaration = new PermissionDeclaration(
                "sales-abc12345.orders.create",
                "Create sales orders"
        );

        // Act
        Set<?> violations = validator.validate(declaration);

        // Assert
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_WithHyphenInResourceSegment_ShouldRejectPermissionCode() {
        // Arrange
        PermissionDeclaration declaration = new PermissionDeclaration(
                "sales-abc12345.order-items.create",
                "Create sales orders"
        );

        // Act
        Set<?> violations = validator.validate(declaration);

        // Assert
        assertThat(violations).hasSize(1);
    }
}
