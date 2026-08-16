package com.cobre.notifications.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NotificationDeliveryWorkerPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void configureValidation() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidation() {
        validatorFactory.close();
    }

    @Test
    void acceptsAConsistentWorkerConfiguration() {
        NotificationDeliveryWorkerProperties properties =
                properties("worker-1", 10, Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(2));

        assertThat(validator.validate(properties)).isEmpty();
        assertThat(properties.claimCommand().workerId()).isEqualTo("worker-1");
        assertThat(properties.claimCommand().batchSize()).isEqualTo(10);
        assertThat(properties.claimCommand().leaseDuration()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void rejectsAnInvalidWorkerIdentityAndBatchSize() {
        NotificationDeliveryWorkerProperties properties =
                properties(" ", 101, Duration.ofSeconds(1), Duration.ZERO, Duration.ofMinutes(2));

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder("workerId is required", "batchSize must be between 1 and 100");
    }

    @Test
    void rejectsInvalidWorkerDurations() {
        NotificationDeliveryWorkerProperties properties =
                properties("worker-1", 10, Duration.ZERO, Duration.ofSeconds(-1), Duration.ZERO);

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "pollInterval must be positive",
                        "initialDelay must not be negative",
                        "leaseDuration must be positive");
    }

    private NotificationDeliveryWorkerProperties properties(
            String workerId, int batchSize, Duration pollInterval, Duration initialDelay, Duration leaseDuration) {
        return new NotificationDeliveryWorkerProperties(
                true, workerId, batchSize, pollInterval, initialDelay, leaseDuration);
    }
}
