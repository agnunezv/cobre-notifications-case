package com.cobre.notifications.application.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClaimNotificationDeliveriesCommandTest {

    @ParameterizedTest
    @ValueSource(ints = {1, ClaimNotificationDeliveriesCommand.MAX_BATCH_SIZE})
    void acceptsSupportedBatchSizes(int batchSize) {
        assertThatCode(() -> new ClaimNotificationDeliveriesCommand(
                "worker-1",
                batchSize,
                Duration.ofSeconds(30)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsBlankWorkerIdentifiers(String workerId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClaimNotificationDeliveriesCommand(
                        workerId,
                        10,
                        Duration.ofSeconds(30)))
                .withMessage("workerId is required");
    }

    @Test
    void rejectsWorkerIdentifiersThatDoNotFitTheDatabaseColumn() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClaimNotificationDeliveriesCommand(
                        "w".repeat(129),
                        10,
                        Duration.ofSeconds(30)))
                .withMessage("workerId must not exceed 128 characters");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsUnsupportedBatchSizes(int batchSize) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClaimNotificationDeliveriesCommand(
                        "worker-1",
                        batchSize,
                        Duration.ofSeconds(30)))
                .withMessage("batchSize must be between 1 and 100");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveLeaseDurations(long seconds) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClaimNotificationDeliveriesCommand(
                        "worker-1",
                        10,
                        Duration.ofSeconds(seconds)))
                .withMessage("leaseDuration must be positive");
    }
}
