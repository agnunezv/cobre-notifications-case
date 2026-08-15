package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record NotificationDeliveryAttemptCompletion(
        @NotNull @Valid PreparedNotificationDelivery delivery,
        @NotNull @Valid WebhookDeliveryOutcome outcome,
        @NotNull DeliveryStatus nextStatus,
        Instant nextAttemptAt,
        @NotNull Instant finishedAt) {

    @AssertTrue(message = "nextStatus must match the delivery outcome")
    public boolean isOutcomeConsistent() {
        if (outcome == null || outcome.result() == null || nextStatus == null) {
            return true;
        }

        return switch (outcome.result()) {
            case SUCCESS -> nextStatus == DeliveryStatus.COMPLETED;
            case PERMANENT_FAILURE -> nextStatus == DeliveryStatus.FAILED;
            case RETRYABLE_FAILURE -> nextStatus == DeliveryStatus.RETRY_SCHEDULED
                    || nextStatus == DeliveryStatus.FAILED;
        };
    }

    @AssertTrue(message = "nextAttemptAt is required only for a future scheduled retry")
    public boolean isRetryScheduleConsistent() {
        if (nextStatus == null || finishedAt == null) {
            return true;
        }
        if (nextStatus == DeliveryStatus.RETRY_SCHEDULED) {
            return nextAttemptAt != null && nextAttemptAt.isAfter(finishedAt);
        }
        return nextAttemptAt == null;
    }
}
