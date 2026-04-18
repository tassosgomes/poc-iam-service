package com.platform.authz.sdk.registration;

import com.platform.authz.sdk.AuthzClient;
import com.platform.authz.sdk.dto.SyncRequest;
import com.platform.authz.sdk.exception.AuthzClientException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Performs module self-registration during application startup.
 */
public class SelfRegistrationRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfRegistrationRunner.class);
    private static final String RETRY_NAME = "authz-registration";
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(32);
    private static final Duration DEFAULT_RETRY_WINDOW = Duration.ofMinutes(5);

    private final AuthzClient authzClient;
    private final PermissionsYamlLoader permissionsYamlLoader;
    private final ReadinessGate readinessGate;
    private final Retry retry;

    public SelfRegistrationRunner(
            AuthzClient authzClient,
            PermissionsYamlLoader permissionsYamlLoader,
            ReadinessGate readinessGate,
            RetryRegistry retryRegistry
    ) {
        this(
                authzClient,
                permissionsYamlLoader,
                readinessGate,
                retryRegistry,
                DEFAULT_INITIAL_DELAY,
                DEFAULT_MAX_DELAY,
                DEFAULT_RETRY_WINDOW
        );
    }

    SelfRegistrationRunner(
            AuthzClient authzClient,
            PermissionsYamlLoader permissionsYamlLoader,
            ReadinessGate readinessGate,
            RetryRegistry retryRegistry,
            Duration initialDelay,
            Duration maxDelay,
            Duration retryWindow
    ) {
        this.authzClient = Objects.requireNonNull(authzClient, "authzClient must not be null");
        this.permissionsYamlLoader = Objects.requireNonNull(
                permissionsYamlLoader,
                "permissionsYamlLoader must not be null"
        );
        this.readinessGate = Objects.requireNonNull(readinessGate, "readinessGate must not be null");
        Objects.requireNonNull(retryRegistry, "retryRegistry must not be null");

        this.retry = retryRegistry.retry(
                RETRY_NAME,
                buildRetryConfig(initialDelay, maxDelay, retryWindow)
        );
        this.retry.getEventPublisher().onRetry(event -> LOGGER.info(
                "authz.registration.attempt sequence={} delay_ms={}",
                event.getNumberOfRetryAttempts() + 1,
                event.getWaitInterval().toMillis()
        ));
    }

    @Override
    public void run(ApplicationArguments args) {
        syncOnStartup();
    }

    public void syncOnStartup() {
        executeSync(ExecutionMode.STARTUP);
    }

    public void syncOnHeartbeat() {
        executeSync(ExecutionMode.HEARTBEAT);
    }

    private void executeSync(ExecutionMode executionMode) {
        PermissionsYamlLoader.PermissionsDocument document;
        try {
            document = permissionsYamlLoader.load();
        } catch (PermissionsYamlLoader.InvalidPermissionsFileException exception) {
            if (executionMode == ExecutionMode.STARTUP) {
                throw exception;
            }

            LOGGER.warn("Heartbeat self-registration skipped due to invalid permissions file: {}", exception.getMessage());
            return;
        }

        SyncRequest request = toSyncRequest(document);
        LOGGER.info("authz.registration.attempt sequence={} delay_ms={}", 1, 0);

        try {
            Retry.decorateRunnable(retry, () -> authzClient.sync(request)).run();
            readinessGate.markReady();
        } catch (RuntimeException exception) {
            logFailure(executionMode, request.moduleId(), exception);
        }
    }

    private SyncRequest toSyncRequest(PermissionsYamlLoader.PermissionsDocument document) {
        String canonicalPayload = permissionsYamlLoader.canonicalize(document);
        String payloadHash = sha256Hex(canonicalPayload);
        return new SyncRequest(
                document.moduleId(),
                document.schemaVersion(),
                payloadHash,
                document.permissions()
        );
    }

    private void logFailure(ExecutionMode executionMode, String moduleId, RuntimeException exception) {
        if (exception instanceof AuthzClientException authzClientException
                && (authzClientException.getStatusCode() == 401 || authzClientException.getStatusCode() == 403)) {
            logByMode(
                    executionMode,
                    "Self-registration failed with terminal status={} for moduleId={}",
                    authzClientException,
                    authzClientException.getStatusCode(),
                    moduleId
            );
            return;
        }

        logByMode(
                executionMode,
                "Self-registration did not complete for moduleId={}",
                exception,
                moduleId
        );
    }

    private void logByMode(
            ExecutionMode executionMode,
            String message,
            RuntimeException exception,
            Object... arguments
    ) {
        Object[] logArguments = new Object[arguments.length + 1];
        System.arraycopy(arguments, 0, logArguments, 0, arguments.length);
        logArguments[arguments.length] = exception;

        if (executionMode == ExecutionMode.HEARTBEAT) {
            LOGGER.warn(message, logArguments);
            return;
        }

        LOGGER.error(message, logArguments);
    }

    private RetryConfig buildRetryConfig(Duration initialDelay, Duration maxDelay, Duration retryWindow) {
        long initialDelayMillis = Math.max(1L, requirePositive(initialDelay, "initialDelay").toMillis());
        long maxDelayMillis = Math.max(initialDelayMillis, requirePositive(maxDelay, "maxDelay").toMillis());
        long retryWindowMillis = Math.max(initialDelayMillis, requirePositive(retryWindow, "retryWindow").toMillis());

        return RetryConfig.custom()
                .maxAttempts(calculateMaxAttempts(initialDelayMillis, maxDelayMillis, retryWindowMillis))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(initialDelayMillis, 2.0d, maxDelayMillis))
                .retryOnException(SelfRegistrationRunner::isRetryable)
                .failAfterMaxAttempts(true)
                .build();
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static boolean isRetryable(Throwable throwable) {
        if (throwable instanceof AuthzClientException authzClientException) {
            return authzClientException.isServerError() || authzClientException.getStatusCode() == 0;
        }

        return true;
    }

    private static int calculateMaxAttempts(long initialDelayMillis, long maxDelayMillis, long retryWindowMillis) {
        long totalWaitMillis = 0L;
        long currentDelayMillis = initialDelayMillis;
        int retries = 0;

        while (retries == 0 || totalWaitMillis + currentDelayMillis <= retryWindowMillis) {
            totalWaitMillis += currentDelayMillis;
            retries++;
            currentDelayMillis = Math.min(Math.max(1L, currentDelayMillis * 2L), maxDelayMillis);
        }

        return retries + 1;
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }

    private enum ExecutionMode {
        STARTUP,
        HEARTBEAT
    }
}
