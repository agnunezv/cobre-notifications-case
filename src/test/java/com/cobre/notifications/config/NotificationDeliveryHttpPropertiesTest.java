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

class NotificationDeliveryHttpPropertiesTest {

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
    void acceptsPositiveTimeouts() {
        NotificationDeliveryHttpProperties properties =
                new NotificationDeliveryHttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(5));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsANonPositiveConnectTimeout() {
        NotificationDeliveryHttpProperties properties =
                new NotificationDeliveryHttpProperties(Duration.ZERO, Duration.ofSeconds(5));

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("connectTimeout must be positive");
    }

    @Test
    void rejectsANonPositiveResponseTimeout() {
        NotificationDeliveryHttpProperties properties =
                new NotificationDeliveryHttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(-1));

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("responseTimeout must be positive");
    }
}
