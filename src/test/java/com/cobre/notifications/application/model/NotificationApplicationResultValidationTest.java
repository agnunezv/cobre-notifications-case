package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationApplicationResultValidationTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
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
    void validatesClaimedDeliveryDataReturnedByPersistence() {
        ClaimedNotificationDelivery delivery = new ClaimedNotificationDelivery(
                " ",
                "CLIENT001",
                "credit_payment",
                "Payment confirmed",
                0,
                null);

        assertThat(validator.validate(delivery))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("eventId", "deliveryCycle", "leaseUntil");
    }

    @Test
    void cascadesPageValidationToItsItems() {
        NotificationEventSummary summary = new NotificationEventSummary(
                " ",
                "credit_payment",
                NOW,
                null,
                DeliveryStatus.PENDING);
        NotificationEventPage page = new NotificationEventPage(List.of(summary), 0, 20, false);

        assertThat(validator.validate(page))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("items[0].eventId");
    }

    @Test
    void keepsPageItemsImmutable() {
        NotificationEventSummary summary = new NotificationEventSummary(
                "EVT001",
                "credit_payment",
                NOW,
                null,
                DeliveryStatus.PENDING);
        List<NotificationEventSummary> mutableItems = new java.util.ArrayList<>(List.of(summary));

        NotificationEventPage page = new NotificationEventPage(mutableItems, 0, 20, false);
        mutableItems.clear();

        assertThat(page.items()).containsExactly(summary);
    }
}
