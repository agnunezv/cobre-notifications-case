package com.cobre.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NotificationSubscriptionTest {

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
    void acceptsAnAbsoluteHttpsEndpoint() {
        NotificationSubscription subscription = new NotificationSubscription(
                "SUBSCRIPTION001",
                "CLIENT001",
                URI.create("https://hooks.example.com:8443/notifications?source=cobre"));

        assertThat(subscription.endpointUrl())
                .isEqualTo(URI.create("https://hooks.example.com:8443/notifications?source=cobre"));
        assertThat(validator.validate(subscription)).isEmpty();
    }

    @Test
    void rejectsAnHttpEndpoint() {
        Set<ConstraintViolation<NotificationSubscription>> violations =
                validator.validate(subscriptionWithEndpoint("http://hooks.example.com/notifications"));

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("endpointUrl must be an absolute HTTPS URL without user information or a fragment");
    }

    @Test
    void rejectsEndpointsWithUserInformation() {
        assertThat(validator.validate(
                        subscriptionWithEndpoint("https://user:password@hooks.example.com/notifications")))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("endpointUrl must be an absolute HTTPS URL without user information or a fragment");
    }

    @Test
    void rejectsEndpointsWithFragments() {
        assertThat(validator.validate(subscriptionWithEndpoint("https://hooks.example.com/notifications#internal")))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("endpointUrl must be an absolute HTTPS URL without user information or a fragment");
    }

    @Test
    void rejectsBlankRequiredFields() {
        NotificationSubscription subscription =
                new NotificationSubscription(" ", " ", URI.create("https://hooks.example.com/notifications"));

        assertThat(validator.validate(subscription))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("subscriptionId", "clientId");
    }

    @Test
    void requiresAnEndpoint() {
        NotificationSubscription subscription = new NotificationSubscription("SUBSCRIPTION001", "CLIENT001", null);

        assertThat(validator.validate(subscription))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("endpointUrl");
    }

    @Test
    void doesNotExposeMalformedStoredEndpointTextInTheExceptionMessage() {
        assertThatExceptionOfType(InvalidNotificationSubscriptionException.class)
                .isThrownBy(() -> NotificationSubscription.fromStoredValues(
                        "SUBSCRIPTION001", "CLIENT001", "https://user:secret@[invalid"))
                .withMessage("Subscription SUBSCRIPTION001 has an invalid endpoint URL")
                .withMessageNotContaining("secret");
    }

    private NotificationSubscription subscriptionWithEndpoint(String endpointUrl) {
        return new NotificationSubscription("SUBSCRIPTION001", "CLIENT001", URI.create(endpointUrl));
    }
}
