package com.cs.eventgateway.service.ledger;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryBackoffPolicyTest {

    private final RetryBackoffPolicy retryBackoffPolicy = new RetryBackoffPolicy();

    @Test
    void nextBackoff_whenAttemptCountIsZeroOrNegative_returnsOneMinuteMinimum() {
        assertThat(retryBackoffPolicy.nextBackoff(0)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryBackoffPolicy.nextBackoff(-1)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void nextBackoff_whenAttemptsIncrease_returnsExponentialDelay() {
        assertThat(retryBackoffPolicy.nextBackoff(1)).isEqualTo(Duration.ofMinutes(1));
        assertThat(retryBackoffPolicy.nextBackoff(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(retryBackoffPolicy.nextBackoff(3)).isEqualTo(Duration.ofMinutes(4));
        assertThat(retryBackoffPolicy.nextBackoff(5)).isEqualTo(Duration.ofMinutes(16));
    }

    @Test
    void nextBackoff_whenCalculatedDelayExceedsLimit_capsAtThirtyMinutes() {
        assertThat(retryBackoffPolicy.nextBackoff(6)).isEqualTo(Duration.ofMinutes(30));
        assertThat(retryBackoffPolicy.nextBackoff(20)).isEqualTo(Duration.ofMinutes(30));
    }
}
