package com.cobre.notifications.application.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimNotificationDeliveriesCommandTest {

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

    @ParameterizedTest
    @ValueSource(ints = {1, ClaimNotificationDeliveriesCommand.MAX_BATCH_SIZE})
    void acceptsSupportedBatchSizes(int batchSize) {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                "worker-1",
                batchSize,
                Duration.ofSeconds(30));

        assertThat(validator.validate(command)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void rejectsBlankWorkerIdentifiers(String workerId) {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                workerId,
                10,
                Duration.ofSeconds(30));

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .contains("workerId is required");
    }

    @Test
    void rejectsWorkerIdentifiersThatDoNotFitTheDatabaseColumn() {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                "w".repeat(129),
                10,
                Duration.ofSeconds(30));

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("workerId must not exceed 128 characters");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void rejectsUnsupportedBatchSizes(int batchSize) {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                "worker-1",
                batchSize,
                Duration.ofSeconds(30));

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("batchSize must be between 1 and 100");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveLeaseDurations(long seconds) {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                "worker-1",
                10,
                Duration.ofSeconds(seconds));

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("leaseDuration must be positive");
    }

    @Test
    void requiresALeaseDuration() {
        ClaimNotificationDeliveriesCommand command = new ClaimNotificationDeliveriesCommand(
                "worker-1",
                10,
                null);

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("leaseDuration is required");
    }
}
