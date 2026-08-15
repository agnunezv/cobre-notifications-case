package com.cobre.notifications.application.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record ExpiredNotificationLease(
        @NotBlank @Size(max = 64) String eventId,
        @Positive int deliveryCycle,
        @NotBlank @Size(max = 128) String previousWorkerId,
        @NotNull Instant leaseUntil,
        UUID openAttemptId,
        @Positive Integer openAttemptNumber,
        Instant openAttemptStartedAt) {

    @AssertTrue(message = "open attempt fields must either all be present or all be absent")
    public boolean isOpenAttemptConsistent() {
        int presentFields = (openAttemptId == null ? 0 : 1)
                + (openAttemptNumber == null ? 0 : 1)
                + (openAttemptStartedAt == null ? 0 : 1);
        return presentFields == 0 || presentFields == 3;
    }

    public boolean hasOpenAttempt() {
        return openAttemptId != null;
    }
}
