package com.platform.authz.sdk.registration;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that stays DOWN until the first successful catalog sync.
 */
public class ReadinessGate implements HealthIndicator {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public void markReady() {
        ready.set(true);
    }

    public boolean isReady() {
        return ready.get();
    }

    @Override
    public Health health() {
        if (!ready.get()) {
            return Health.down()
                    .withDetail("reason", "awaiting-first-sync")
                    .build();
        }

        return Health.up()
                .withDetail("reason", "first-sync-complete")
                .build();
    }
}
