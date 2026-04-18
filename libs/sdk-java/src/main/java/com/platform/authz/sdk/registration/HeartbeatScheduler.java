package com.platform.authz.sdk.registration;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodically re-synchronises the permission catalog to detect drift.
 */
public class HeartbeatScheduler {

    static final long FIFTEEN_MINUTES_MILLIS = 15L * 60L * 1000L;

    private final SelfRegistrationRunner selfRegistrationRunner;

    public HeartbeatScheduler(SelfRegistrationRunner selfRegistrationRunner) {
        this.selfRegistrationRunner = Objects.requireNonNull(
                selfRegistrationRunner,
                "selfRegistrationRunner must not be null"
        );
    }

    @Scheduled(fixedRate = FIFTEEN_MINUTES_MILLIS)
    public void sendHeartbeat() {
        selfRegistrationRunner.syncOnHeartbeat();
    }
}
