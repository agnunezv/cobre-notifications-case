package com.cobre.notifications.application.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventQueryTest {

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
    void acceptsAValidQuery() {
        NotificationEventQuery query = new NotificationEventQuery(
                "CLIENT001",
                Instant.parse("2026-08-15T10:00:00Z"),
                Instant.parse("2026-08-15T11:00:00Z"),
                null,
                0,
                20);

        assertThat(validator.validate(query)).isEmpty();
    }

    @Test
    void rejectsAnInvalidDateRange() {
        NotificationEventQuery query = new NotificationEventQuery(
                "CLIENT001",
                Instant.parse("2026-08-15T11:00:00Z"),
                Instant.parse("2026-08-15T10:00:00Z"),
                null,
                0,
                20);

        assertThat(validator.validate(query))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("created_from must be earlier than created_to");
    }

    @Test
    void rejectsInvalidClientAndPaginationValues() {
        NotificationEventQuery query = new NotificationEventQuery(" ", null, null, null, -1, 101);

        assertThat(validator.validate(query))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "An authenticated client is required",
                        "page must be greater than or equal to zero",
                        "size must be between 1 and 100");
    }

    @Test
    void acceptsAValidDetailsQuery() {
        NotificationEventDetailsQuery query = new NotificationEventDetailsQuery(
                "CLIENT001",
                "EVENT001");

        assertThat(validator.validate(query)).isEmpty();
    }

    @Test
    void rejectsInvalidDetailsQueryIdentifiers() {
        NotificationEventDetailsQuery query = new NotificationEventDetailsQuery(
                " ",
                "E".repeat(65));

        assertThat(validator.validate(query))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "An authenticated client is required",
                        "notificationEventId must not exceed 64 characters");
    }

    @Test
    void acceptsAValidReplayCommand() {
        ReplayNotificationEventCommand command = new ReplayNotificationEventCommand(
                "CLIENT001",
                "EVENT001");

        assertThat(validator.validate(command)).isEmpty();
    }

    @Test
    void rejectsInvalidReplayCommandIdentifiers() {
        ReplayNotificationEventCommand command = new ReplayNotificationEventCommand(
                " ",
                "E".repeat(65));

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "An authenticated client is required",
                        "notificationEventId must not exceed 64 characters");
    }
}
