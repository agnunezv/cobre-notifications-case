package com.cobre.notifications.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NotificationSubscriptionQueryTest {

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
    void requiresAClientId() {
        NotificationSubscriptionQuery query = new NotificationSubscriptionQuery(" ", "credit_payment");

        assertThat(validator.validate(query))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("clientId");
    }

    @Test
    void requiresAnEventType() {
        NotificationSubscriptionQuery query = new NotificationSubscriptionQuery("CLIENT001", null);

        assertThat(validator.validate(query))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("eventType");
    }
}
