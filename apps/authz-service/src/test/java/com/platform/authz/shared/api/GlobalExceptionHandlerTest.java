package com.platform.authz.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ProblemDetailFactory());
    }

    @Test
    void handleBindException_WithModelAttributeValidationFailure_ShouldReturnBadRequestProblemDetail() {
        // Arrange
        HttpServletRequest request = new MockHttpServletRequest("GET", "/v1/audit/events");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "queryParams");
        bindingResult.addError(new FieldError("queryParams", "moduleId", "Failed to convert value"));
        BindException exception = new BindException(bindingResult);

        // Act
        var response = handler.handleBindException(exception, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTitle()).isEqualTo("Validation error");
        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.getType()).isEqualTo(URI.create("https://authz.platform/errors/validation-error"));
        assertThat(body.getInstance()).isEqualTo(URI.create("/v1/audit/events"));
        assertThat(body.getProperties()).containsEntry("errors", Map.of("moduleId", "Failed to convert value"));
    }
}
