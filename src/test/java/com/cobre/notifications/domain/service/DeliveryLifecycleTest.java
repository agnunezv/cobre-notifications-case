package com.cobre.notifications.domain.service;

import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DeliveryLifecycleTest {

    private final DeliveryLifecycle lifecycle = new DeliveryLifecycle(new RetryPolicy(3));

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = {"PENDING", "RETRY_SCHEDULED"})
    void claimsReadyDeliveries(DeliveryStatus currentStatus) {
        assertThat(lifecycle.claim(currentStatus)).isEqualTo(DeliveryStatus.PROCESSING);
    }

    @ParameterizedTest
    @EnumSource(
            value = DeliveryStatus.class,
            names = {"PENDING", "RETRY_SCHEDULED"},
            mode = EnumSource.Mode.EXCLUDE)
    void rejectsClaimFromStatusesThatAreNotReady(DeliveryStatus currentStatus) {
        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycle.claim(currentStatus))
                .withMessage("Cannot claim delivery in %s status", currentStatus);
    }

    @Test
    void completesTheDeliveryAfterASuccessfulAttempt() {
        DeliveryStatus nextStatus = lifecycle.finishAttempt(
                DeliveryStatus.PROCESSING,
                DeliveryAttemptResult.SUCCESS,
                1);

        assertThat(nextStatus).isEqualTo(DeliveryStatus.COMPLETED);
    }

    @Test
    void schedulesAnotherAttemptAfterARetryableFailure() {
        DeliveryStatus nextStatus = lifecycle.finishAttempt(
                DeliveryStatus.PROCESSING,
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                2);

        assertThat(nextStatus).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
    }

    @Test
    void failsTheDeliveryWhenTheRetryPolicyIsExhausted() {
        DeliveryStatus nextStatus = lifecycle.finishAttempt(
                DeliveryStatus.PROCESSING,
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                3);

        assertThat(nextStatus).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void failsTheDeliveryImmediatelyAfterAPermanentFailure() {
        DeliveryStatus nextStatus = lifecycle.finishAttempt(
                DeliveryStatus.PROCESSING,
                DeliveryAttemptResult.PERMANENT_FAILURE,
                1);

        assertThat(nextStatus).isEqualTo(DeliveryStatus.FAILED);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = "PROCESSING", mode = EnumSource.Mode.EXCLUDE)
    void rejectsAttemptResultsOutsideProcessing(DeliveryStatus currentStatus) {
        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycle.finishAttempt(
                        currentStatus,
                        DeliveryAttemptResult.SUCCESS,
                        1))
                .withMessage("Cannot finish an attempt for delivery in %s status", currentStatus);
    }

    @Test
    void startsANewPendingCycleWhenAFailedDeliveryIsReplayed() {
        assertThat(lifecycle.replay(DeliveryStatus.FAILED)).isEqualTo(DeliveryStatus.PENDING);
    }

    @ParameterizedTest
    @EnumSource(value = DeliveryStatus.class, names = "FAILED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsReplayForDeliveriesThatHaveNotFailed(DeliveryStatus currentStatus) {
        assertThatIllegalStateException()
                .isThrownBy(() -> lifecycle.replay(currentStatus))
                .withMessage("Cannot replay delivery in %s status", currentStatus);
    }

    @Test
    void requiresAtLeastOneCompletedAttempt() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> lifecycle.finishAttempt(
                        DeliveryStatus.PROCESSING,
                        DeliveryAttemptResult.RETRYABLE_FAILURE,
                        0))
                .withMessage("completedAttempts must be at least 1");
    }

    @Test
    void requiresAValidMaximumNumberOfAttempts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RetryPolicy(0))
                .withMessage("maximumAttempts must be at least 1");
    }
}
