package com.platform.authz.authz.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.authz.authz.api.dto.CheckRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("perf")
@Tag("PerformanceTest")
@Testcontainers(disabledWithoutDocker = true)
class CheckEndpointPerformanceTest {

    private static final int DISTINCT_PERMISSION_COUNT = 200;
    private static final int ROLE_COUNT = 50;
    private static final int REQUEST_COUNT = 500;
    private static final long P95_THRESHOLD_MS = 50L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("authz")
            .withUsername("authz")
            .withPassword("authz");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTHZ_DB_URL", postgres::getJdbcUrl);
        registry.add("AUTHZ_DB_USER", postgres::getUsername);
        registry.add("AUTHZ_DB_PASS", postgres::getPassword);
        registry.add("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI", () -> "https://issuer.example.test");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_role");
        jdbcTemplate.update("DELETE FROM role_permission");
        jdbcTemplate.update("DELETE FROM role");
        jdbcTemplate.update("DELETE FROM permission");
        jdbcTemplate.update("DELETE FROM module_key");
        jdbcTemplate.update("DELETE FROM module");
    }

    @Test
    void authzCheck_WithSeededDataset_ShouldMeetP95Target() throws Exception {
        // Arrange
        String userId = "perf-check-user";
        String targetPermission = "perf.resource.050.read";
        seedDataset(userId);

        CheckRequest request = new CheckRequest(userId, targetPermission);
        String requestBody = objectMapper.writeValueAsString(request);

        // Warm-up: first call populates the cache
        mockMvc.perform(post("/v1/authz/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId)
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        // Act
        List<Long> latencies = new ArrayList<>(REQUEST_COUNT);
        for (int index = 0; index < REQUEST_COUNT; index++) {
            long startedAt = System.nanoTime();
            mockMvc.perform(post("/v1/authz/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(userId)
                                    .claim("roles", List.of("USER")))))
                    .andExpect(status().isOk());
            latencies.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt));
        }

        // Assert
        long p95Millis = calculateP95Millis(latencies);
        assertThat(p95Millis).isLessThan(P95_THRESHOLD_MS);
        assertThat(meterRegistry.find("authz_check_seconds").timer()).isNotNull();
        assertThat(meterRegistry.find("authz_check_seconds").timer().count())
                .isGreaterThanOrEqualTo(REQUEST_COUNT);
    }

    @Test
    void authzCheck_WithDeniedPermission_ShouldReturnDenied() throws Exception {
        // Arrange
        String userId = "perf-denied-user";
        seedDataset(userId);

        CheckRequest request = new CheckRequest(userId, "nonexistent.permission");
        String requestBody = objectMapper.writeValueAsString(request);

        // Act & Assert
        mockMvc.perform(post("/v1/authz/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .with(jwt().jwt(jwt -> jwt
                                .subject(userId)
                                .claim("roles", List.of("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.source").value("denied"));
    }

    private void seedDataset(String userId) {
        UUID moduleId = UUID.randomUUID();
        Instant now = Instant.parse("2026-04-20T12:00:00Z");

        jdbcTemplate.update(
                """
                        INSERT INTO module (id, name, allowed_prefix, description, created_by, created_at, last_heartbeat_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                moduleId,
                "Check Perf Module " + moduleId,
                "perf" + moduleId.toString().substring(0, 8),
                "Check performance testing module",
                "seed",
                Timestamp.from(now),
                Timestamp.from(now)
        );

        List<UUID> permissionIds = new ArrayList<>(DISTINCT_PERMISSION_COUNT);
        List<Object[]> permissionRows = new ArrayList<>(DISTINCT_PERMISSION_COUNT);
        for (int index = 0; index < DISTINCT_PERMISSION_COUNT; index++) {
            UUID permissionId = UUID.randomUUID();
            permissionIds.add(permissionId);
            String status = index % 10 == 0 ? "DEPRECATED" : "ACTIVE";
            permissionRows.add(new Object[]{
                    permissionId,
                    moduleId,
                    "perf.resource.%03d.read".formatted(index),
                    "Performance permission %03d".formatted(index),
                    status,
                    null,
                    Timestamp.from(now),
                    Timestamp.from(now)
            });
        }

        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO permission (
                            id, module_id, code, description, status, sunset_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                permissionRows
        );

        List<Object[]> roleRows = new ArrayList<>(ROLE_COUNT);
        List<Object[]> rolePermissionRows = new ArrayList<>(ROLE_COUNT * (DISTINCT_PERMISSION_COUNT / ROLE_COUNT));
        List<Object[]> userRoleRows = new ArrayList<>(ROLE_COUNT);
        for (int index = 0; index < ROLE_COUNT; index++) {
            UUID roleId = UUID.randomUUID();

            roleRows.add(new Object[]{
                    roleId,
                    moduleId,
                    "CHECK_PERF_ROLE_%03d".formatted(index),
                    "Check perf role %03d".formatted(index),
                    "seed",
                    Timestamp.from(now)
            });

            int permsPerRole = DISTINCT_PERMISSION_COUNT / ROLE_COUNT;
            for (int permIndex = 0; permIndex < permsPerRole; permIndex++) {
                UUID permissionId = permissionIds.get(index * permsPerRole + permIndex);
                rolePermissionRows.add(new Object[]{roleId, permissionId});
            }

            userRoleRows.add(new Object[]{
                    UUID.randomUUID(),
                    userId,
                    roleId,
                    "seed",
                    Timestamp.from(now),
                    null,
                    null
            });
        }

        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO role (id, module_id, name, description, created_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                roleRows
        );
        jdbcTemplate.batchUpdate(
                "INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)",
                rolePermissionRows
        );
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO user_role (
                            id, user_id, role_id, assigned_by, assigned_at, revoked_at, revoked_by
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                userRoleRows
        );
    }

    private long calculateP95Millis(List<Long> latenciesMicros) {
        latenciesMicros.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95d * latenciesMicros.size()) - 1;
        return TimeUnit.MICROSECONDS.toMillis(latenciesMicros.get(p95Index));
    }
}
