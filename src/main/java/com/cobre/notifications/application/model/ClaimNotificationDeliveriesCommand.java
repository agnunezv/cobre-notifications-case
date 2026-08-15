package com.cobre.notifications.application.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;

public record ClaimNotificationDeliveriesCommand(
        @NotBlank(message = "workerId is required")
        @Size(max = 128, message = "workerId must not exceed 128 characters")
        String workerId,
        @Min(value = 1, message = "batchSize must be between 1 and 100")
        @Max(value = MAX_BATCH_SIZE, message = "batchSize must be between 1 and 100")
        int batchSize,
        @NotNull(message = "leaseDuration is required")
        Duration leaseDuration) {

    public static final int MAX_BATCH_SIZE = 100;

    @AssertTrue(message = "leaseDuration must be positive")
    public boolean isLeaseDurationPositive() {
        return leaseDuration == null || !leaseDuration.isZero() && !leaseDuration.isNegative();
    }
}
