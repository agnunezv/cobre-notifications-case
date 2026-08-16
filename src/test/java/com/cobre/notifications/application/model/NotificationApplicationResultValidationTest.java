package com.cobre.notifications.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationDestination;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
                " ", "CLIENT001", "credit_payment", "Payment confirmed", 0, "worker-1", null, false, null);

        assertThat(validator.validate(delivery))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("eventId", "deliveryCycle", "leaseUntil");
    }

    @Test
    void cascadesPageValidationToItsItems() {
        NotificationEventSummary summary =
                new NotificationEventSummary(" ", "credit_payment", NOW, null, DeliveryStatus.PENDING);
        NotificationEventPage page = new NotificationEventPage(List.of(summary), 0, 20, false);

        assertThat(validator.validate(page))
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .containsExactly("items[0].eventId");
    }

    @Test
    void keepsPageItemsImmutable() {
        NotificationEventSummary summary =
                new NotificationEventSummary("EVT001", "credit_payment", NOW, null, DeliveryStatus.PENDING);
        List<NotificationEventSummary> mutableItems = new java.util.ArrayList<>(List.of(summary));

        NotificationEventPage page = new NotificationEventPage(mutableItems, 0, 20, false);
        mutableItems.clear();

        assertThat(page.items()).containsExactly(summary);
    }

    @Test
    void validatesNotificationEventDetailsReturnedByPersistence() {
        NotificationEventDetails details = new NotificationEventDetails(
                "EVT001", "credit_payment", "Payment confirmed", NOW, null, DeliveryStatus.COMPLETED);

        assertThat(validator.validate(details))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("deliveryDate is required when deliveryStatus is final");
    }

    @Test
    void validatesTheStatusSelectedForAnAttemptOutcome() {
        NotificationDeliveryAttemptCompletion completion = completion(DeliveryStatus.COMPLETED, null);

        assertThat(validator.validate(completion))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("nextStatus must match the delivery outcome");
    }

    @Test
    void requiresAFutureTimeForAScheduledRetry() {
        NotificationDeliveryAttemptCompletion completion = completion(DeliveryStatus.RETRY_SCHEDULED, NOW);

        assertThat(validator.validate(completion))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("nextAttemptAt is required only for a future scheduled retry");
    }

    @Test
    void acceptsAConsistentScheduledRetry() {
        assertThat(validator.validate(completion(DeliveryStatus.RETRY_SCHEDULED, NOW.plusSeconds(1))))
                .isEmpty();
    }

    @Test
    void requiresEveryClaimedDeliveryToHaveOneBatchOutcome() {
        NotificationDeliveryBatchResult result = new NotificationDeliveryBatchResult(0, 5, 1, 1, 1, 1);

        assertThat(validator.validate(result))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("every claimed delivery must have exactly one batch outcome");
    }

    @Test
    void requiresAllOpenAttemptFieldsForAnExpiredLease() {
        ExpiredNotificationLease expiredLease = new ExpiredNotificationLease(
                "EVT001", 1, "worker-1", NOW.minusSeconds(1), UUID.randomUUID(), null, null);

        assertThat(validator.validate(expiredLease))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("open attempt fields must either all be present or all be absent");
    }

    @Test
    void validatesTheStateProducedByLeaseRecovery() {
        NotificationLeaseRecovery recovery = new NotificationLeaseRecovery(
                new ExpiredNotificationLease("EVT001", 1, "worker-1", NOW.minusSeconds(1), null, null, null),
                DeliveryStatus.RETRY_SCHEDULED,
                null,
                NOW);

        assertThat(validator.validate(recovery))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("nextAttemptAt must be present only for a retry scheduled at or after recovery");
    }

    private NotificationDeliveryAttemptCompletion completion(DeliveryStatus nextStatus, Instant nextAttemptAt) {
        UUID attemptId = UUID.fromString("893c93dc-9fa2-4437-ae4d-f96448af98ad");
        PreparedNotificationDelivery delivery = new PreparedNotificationDelivery(
                attemptId,
                "EVT001",
                "CLIENT001",
                "credit_payment",
                "Payment confirmed",
                new NotificationDestination("SUB001", URI.create("https://hooks.example.com/notifications")),
                1,
                1,
                attemptId.toString(),
                NOW.minusSeconds(1));
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                503,
                NotificationDeliveryFailureCategory.HTTP_RESPONSE,
                "The webhook endpoint returned HTTP 503",
                25);

        return new NotificationDeliveryAttemptCompletion(delivery, outcome, nextStatus, nextAttemptAt, NOW);
    }
}
