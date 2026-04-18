package com.platform.authz.sdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextAccessTokenProviderTest {

    private final RequestContextAccessTokenProvider provider = new RequestContextAccessTokenProvider();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("should resolve bearer token from current request")
    void resolveAccessToken_WithBearerHeader_ShouldReturnToken() {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer jwt-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Act
        var token = provider.resolveAccessToken();

        // Assert
        assertThat(token).contains("jwt-token");
    }

    @Test
    @DisplayName("should return empty when header is missing")
    void resolveAccessToken_WithoutHeader_ShouldReturnEmpty() {
        // Arrange
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        // Act
        var token = provider.resolveAccessToken();

        // Assert
        assertThat(token).isEmpty();
    }
}
