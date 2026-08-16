package com.cobre.notifications.adapter.in.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientPrincipalTest {

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
    void acceptsAConfiguredClientIdentifier() {
        assertThat(validator.validate(new ClientPrincipal("CLIENT001"))).isEmpty();
    }

    @Test
    void rejectsBlankOrOversizedClientIdentifiers() {
        assertThat(validator.validate(new ClientPrincipal(" ")))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("clientId");
        assertThat(validator.validate(new ClientPrincipal("c".repeat(65))))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("clientId");
    }
}
