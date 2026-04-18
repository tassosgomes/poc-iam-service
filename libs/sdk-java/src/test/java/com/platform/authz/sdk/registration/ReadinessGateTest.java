package com.platform.authz.sdk.registration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class ReadinessGateTest {

    @Test
    @DisplayName("health should be down until the first successful sync")
    void health_BeforeFirstSync_ShouldBeDown() {
        // Arrange
        ReadinessGate readinessGate = new ReadinessGate();

        // Act
        var health = readinessGate.health();

        // Assert
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "awaiting-first-sync");
    }

    @Test
    @DisplayName("health should become up after first successful sync")
    void health_AfterFirstSync_ShouldBeUp() {
        // Arrange
        ReadinessGate readinessGate = new ReadinessGate();
        readinessGate.markReady();

        // Act
        var health = readinessGate.health();

        // Assert
        assertThat(readinessGate.isReady()).isTrue();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("reason", "first-sync-complete");
    }
}
