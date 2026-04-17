package com.platform.authz.iam.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Gauge;
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
class BulkFetchPerformanceTest {

    private static final int DISTINCT_PERMISSION_COUNT = 500;
    private static final int USER_ROLE_COUNT = 10_000;
    private static final int REQUEST_COUNT = 1_000;
    private static final long P95_THRESHOLD_MS = 100L;

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
    void bulkFetch_WithSeededDataset_ShouldMeetP95TargetAndExposeCacheMetrics() throws Exception {
        String userId = "perf-user";
        seedDataset(userId);

        List<Long> latencies = new ArrayList<>(REQUEST_COUNT);
        for (int index = 0; index < REQUEST_COUNT; index++) {
            long startedAt = System.nanoTime();
            mockMvc.perform(get("/v1/users/{userId}/permissions", userId)
                            .with(jwt().jwt(jwt -> jwt
                                    .subject(userId)
                                    .claim("roles", List.of("USER")))))
                    .andExpect(status().isOk());
            latencies.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startedAt));
        }

        long p95Millis = calculateP95Millis(latencies);
        Gauge cacheHitRatioGauge = meterRegistry.find("authz_user_permissions_cache_hit_ratio")
                .tag("cache", "userPermissions")
                .gauge();

        assertThat(p95Millis).isLessThan(P95_THRESHOLD_MS);
        assertThat(meterRegistry.find("authz_bulk_fetch_seconds").timer()).isNotNull();
        assertThat(meterRegistry.find("authz_bulk_fetch_seconds").timer().count()).isEqualTo(REQUEST_COUNT);
        assertThat(cacheHitRatioGauge).isNotNull();
        assertThat(cacheHitRatioGauge.value()).isGreaterThan(0.99d);
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
                "Performance Module " + moduleId,
                "perf" + moduleId.toString().substring(0, 8),
                "Performance testing module",
                "seed",
                Timestamp.from(now),
                Timestamp.from(now)
        );

        List<UUID> permissionIds = new ArrayList<>(DISTINCT_PERMISSION_COUNT);
        List<Object[]> permissionRows = new ArrayList<>(DISTINCT_PERMISSION_COUNT);
        for (int index = 0; index < DISTINCT_PERMISSION_COUNT; index++) {
            UUID permissionId = UUID.randomUUID();
            permissionIds.add(permissionId);
            permissionRows.add(new Object[]{
                    permissionId,
                    moduleId,
                    "perf.resource.%03d.read".formatted(index),
                    "Performance permission %03d".formatted(index),
                    "ACTIVE",
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

        List<Object[]> roleRows = new ArrayList<>(USER_ROLE_COUNT);
        List<Object[]> rolePermissionRows = new ArrayList<>(USER_ROLE_COUNT);
        List<Object[]> userRoleRows = new ArrayList<>(USER_ROLE_COUNT);
        for (int index = 0; index < USER_ROLE_COUNT; index++) {
            UUID roleId = UUID.randomUUID();
            UUID permissionId = permissionIds.get(index % DISTINCT_PERMISSION_COUNT);

            roleRows.add(new Object[]{
                    roleId,
                    moduleId,
                    "PERF_ROLE_%05d".formatted(index),
                    "Performance role %05d".formatted(index),
                    "seed",
                    Timestamp.from(now)
            });
            rolePermissionRows.add(new Object[]{roleId, permissionId});
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
