package com.cobre.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30));

    @Test
    void returnsTheConfiguredDelayForEachAutomaticRetry() {
        RetryPolicy policy = new RetryPolicy(4, RETRY_DELAYS);

        assertThat(policy.retryDelayAfter(1)).contains(Duration.ofSeconds(1));
        assertThat(policy.retryDelayAfter(2)).contains(Duration.ofSeconds(5));
        assertThat(policy.retryDelayAfter(3)).contains(Duration.ofSeconds(30));
        assertThat(policy.retryDelayAfter(4)).isEmpty();
    }

    @Test
    void supportsDeliveryWithoutAutomaticRetries() {
        RetryPolicy policy = new RetryPolicy(1, List.of());

        assertThat(policy.hasAnotherAttemptAfter(1)).isFalse();
        assertThat(policy.retryDelayAfter(1)).isEmpty();
    }

    @Test
    void keepsItsDelaySequenceImmutable() {
        List<Duration> mutableDelays = new ArrayList<>(RETRY_DELAYS);

        RetryPolicy policy = new RetryPolicy(4, mutableDelays);
        mutableDelays.clear();

        assertThat(policy.retryDelays()).containsExactlyElementsOf(RETRY_DELAYS);
    }

    @Test
    void requiresAtLeastOneAttempt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(0, List.of()))
                .withMessage("maximumAttempts must be at least 1");
    }

    @Test
    void requiresOneDelayForEveryAutomaticRetry() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(4, List.of(Duration.ofSeconds(1))))
                .withMessage("retryDelays must contain one delay for every automatic retry");
    }

    @Test
    void rejectsNonPositiveRetryDelays() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(2, List.of(Duration.ZERO)))
                .withMessage("retryDelays must contain only positive durations");
    }

    @Test
    void rejectsNullRetryDelays() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(2, Collections.singletonList(null)))
                .withMessage("retryDelays must not contain null entries");
    }
}
