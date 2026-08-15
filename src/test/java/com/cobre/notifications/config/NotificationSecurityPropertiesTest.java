package com.cobre.notifications.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSecurityPropertiesTest {

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
    void allowsAnEmptyTokenToDisableAConfiguredPlaceholder() {
        NotificationSecurityProperties properties = new NotificationSecurityProperties(List.of(
                new NotificationSecurityProperties.ClientCredential("CLIENT001", "")));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void requiresAClientIdentifierWhenATokenIsConfigured() {
        NotificationSecurityProperties properties = new NotificationSecurityProperties(List.of(
                new NotificationSecurityProperties.ClientCredential(" ", "configured-token")));

        assertThat(validator.validate(properties))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("clientId is required when a bearer token is configured");
    }
}
