package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record NotificationLeaseRecovery(
        @NotNull @Valid ExpiredNotificationLease expiredLease,
        @NotNull DeliveryStatus nextStatus,
        Instant nextAttemptAt,
        @NotNull Instant recoveredAt) {

    @AssertTrue(message = "lease recovery must either schedule a retry or fail the delivery") public boolean isNextStatusSupported() {
        return nextStatus == null
                || nextStatus == DeliveryStatus.RETRY_SCHEDULED
                || nextStatus == DeliveryStatus.FAILED;
    }

    @AssertTrue(message = "nextAttemptAt must be present only for a retry scheduled at or after recovery") public boolean isRetryScheduleConsistent() {
        if (nextStatus == null || recoveredAt == null) {
            return true;
        }
        if (nextStatus == DeliveryStatus.RETRY_SCHEDULED) {
            return nextAttemptAt != null && !nextAttemptAt.isBefore(recoveredAt);
        }
        return nextAttemptAt == null;
    }
}
