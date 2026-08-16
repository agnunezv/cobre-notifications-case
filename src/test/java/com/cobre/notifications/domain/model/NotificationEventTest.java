package com.cobre.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationEventTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-15T12:00:00Z");
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

    @ParameterizedTest
    @EnumSource(
            value = DeliveryStatus.class,
            names = {"COMPLETED", "FAILED"},
            mode = EnumSource.Mode.EXCLUDE)
    void allowsNonFinalStatusesWithoutADeliveryDate(DeliveryStatus deliveryStatus) {
        NotificationEvent event = eventWithDelivery(null, deliveryStatus);

        assertThat(validator.validate(event)).isEmpty();
        assertThat(event.deliveryDate()).isNull();
    }

    @Test
    void rejectsBlankRequiredText() {
        Set<ConstraintViolation<NotificationEvent>> violations = validator.validate(eventWithId(" "));

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("eventId");
    }

    @Test
    void requiresACreationDateAndDeliveryStatus() {
        NotificationEvent event = new NotificationEvent(
                "EVT001", "CLIENT001", "credit_card_payment", "Payment received", null, null, null);

        assertThat(validator.validate(event))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("createdAt", "deliveryStatus");
    }

    @ParameterizedTest
    @EnumSource(
            value = DeliveryStatus.class,
            names = {"COMPLETED", "FAILED"})
    void requiresADeliveryDateForFinalStatuses(DeliveryStatus deliveryStatus) {
        NotificationEvent event = eventWithDelivery(null, deliveryStatus);

        assertThat(validator.validate(event))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("deliveryDate is required when deliveryStatus is final");
    }

    @Test
    void allowsAFinalStatusWithADeliveryDate() {
        NotificationEvent event = eventWithDelivery(CREATED_AT.plusSeconds(1), DeliveryStatus.COMPLETED);

        assertThat(validator.validate(event)).isEmpty();
    }

    private NotificationEvent eventWithId(String eventId) {
        return new NotificationEvent(
                eventId,
                "CLIENT001",
                "credit_card_payment",
                "Payment received",
                CREATED_AT,
                null,
                DeliveryStatus.PENDING);
    }

    private NotificationEvent eventWithDelivery(Instant deliveryDate, DeliveryStatus deliveryStatus) {
        return new NotificationEvent(
                "EVT001",
                "CLIENT001",
                "credit_card_payment",
                "Payment received",
                CREATED_AT,
                deliveryDate,
                deliveryStatus);
    }
}
