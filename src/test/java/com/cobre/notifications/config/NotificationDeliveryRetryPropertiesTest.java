package com.cobre.notifications.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryRetryPropertiesTest {

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
    void acceptsOneDelayForEachAutomaticRetry() {
        NotificationDeliveryRetryProperties properties = properties(
                4,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsAnUnsupportedMaximumNumberOfAttempts() {
        NotificationDeliveryRetryProperties properties = properties(11);

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .contains("maximumAttempts must be between 1 and 10");
    }

    @Test
    void rejectsMissingRetryDelays() {
        NotificationDeliveryRetryProperties properties = properties(
                4,
                Duration.ofSeconds(1));

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("delays must contain one entry for every automatic retry");
    }

    @Test
    void rejectsNonPositiveRetryDelays() {
        NotificationDeliveryRetryProperties properties = properties(2, Duration.ZERO);

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("delays must contain only positive durations");
    }

    private NotificationDeliveryRetryProperties properties(int maximumAttempts, Duration... delays) {
        return new NotificationDeliveryRetryProperties(maximumAttempts, List.of(delays));
    }
}
