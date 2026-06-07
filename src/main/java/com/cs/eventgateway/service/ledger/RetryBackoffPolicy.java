package com.cs.eventgateway.service.ledger;

import java.time.Duration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculates retry delays for Account Service apply attempts.
 */
@Slf4j
@Component
public class RetryBackoffPolicy {

    private static final int MAX_RETRY_BACKOFF_MINUTES = 30;

    /**
     * Returns exponential backoff capped at a bounded maximum.
     *
     * @param attemptCount current apply attempt count
     * @return delay before the next retry
     */
    public Duration nextBackoff(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 5);
        long minutes = Math.min(MAX_RETRY_BACKOFF_MINUTES, 1L << exponent);
        Duration backoff = Duration.ofMinutes(minutes);
        log.debug("Calculated retry backoff attempt={} backoff={}", attemptCount, backoff);
        return backoff;
    }
}
