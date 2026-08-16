package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryAttemptOrigin;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryAttemptDetails(
        @NotNull UUID attemptId,
        @Positive int deliveryCycle,
        @Positive int attemptNumber,
        @NotNull DeliveryAttemptOrigin origin,
        @NotNull Instant startedAt,
        Instant finishedAt,
        DeliveryAttemptResult result,
        @Min(100) @Max(599) Integer httpStatus,
        @Size(max = 64) String failureCategory,
        @Size(max = 500) String failureDescription,
        @PositiveOrZero Long latencyMs,
        @NotBlank @Size(max = 128) String correlationId) {
}
