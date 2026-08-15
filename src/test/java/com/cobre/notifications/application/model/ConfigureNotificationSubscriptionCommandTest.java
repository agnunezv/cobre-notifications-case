package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.NotificationSubscription;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigureNotificationSubscriptionCommandTest {

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
    void acceptsAValidSubscriptionConfiguration() {
        ConfigureNotificationSubscriptionCommand command = command(
                URI.create("https://hooks.example.com/notifications"),
                Set.of("credit_transfer"));

        assertThat(validator.validate(command)).isEmpty();
    }

    @Test
    void rejectsAnInvalidDestinationAndEmptyEventTypes() {
        ConfigureNotificationSubscriptionCommand command = command(
                URI.create("http://hooks.example.com/notifications"),
                Set.of());

        assertThat(validator.validate(command))
                .extracting(ConstraintViolation::getMessage)
                .containsExactlyInAnyOrder(
                        "endpointUrl must be an absolute HTTPS URL without user information or a fragment",
                        "must not be empty");
    }

    private ConfigureNotificationSubscriptionCommand command(
            URI endpointUrl,
            Set<String> eventTypes) {
        return new ConfigureNotificationSubscriptionCommand(
                new NotificationSubscription("SUB001", "CLIENT001", endpointUrl),
                eventTypes,
                Instant.parse("2026-08-15T12:00:00Z"));
    }
}
