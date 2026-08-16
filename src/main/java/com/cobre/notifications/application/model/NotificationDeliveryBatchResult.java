package com.cobre.notifications.application.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

public record NotificationDeliveryBatchResult(
        @PositiveOrZero int recoveredLeaseCount,
        @PositiveOrZero int claimedCount,
        @PositiveOrZero int preparationSkippedCount,
        @PositiveOrZero int completionAppliedCount,
        @PositiveOrZero int staleCompletionCount,
        @PositiveOrZero int processingFailureCount) {

    @AssertTrue(message = "every claimed delivery must have exactly one batch outcome") public boolean isCountConsistent() {
        return claimedCount
                == preparationSkippedCount + completionAppliedCount + staleCompletionCount + processingFailureCount;
    }
}
