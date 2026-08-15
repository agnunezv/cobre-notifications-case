package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDeliveryOutcomeTest {

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
    void acceptsAConsistentSuccessfulOutcome() {
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(
                DeliveryAttemptResult.SUCCESS,
                204,
                null,
                null,
                15);

        assertThat(validator.validate(outcome)).isEmpty();
    }

    @Test
    void rejectsASuccessOutcomeWithANonSuccessfulHttpStatus() {
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(
                DeliveryAttemptResult.SUCCESS,
                500,
                null,
                null,
                15);

        assertThat(validator.validate(outcome))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("a successful delivery requires a 2xx status and no failure information");
    }

    @Test
    void rejectsAFailureWithoutFailureInformation() {
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                null,
                null,
                null,
                15);

        assertThat(validator.validate(outcome))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("a failed delivery requires consistent failure information");
    }

    @Test
    void rejectsTransportFailureInformationCombinedWithAnHttpStatus() {
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                503,
                NotificationDeliveryFailureCategory.TIMEOUT,
                "The webhook request timed out",
                15);

        assertThat(validator.validate(outcome))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("a failed delivery requires consistent failure information");
    }
}
