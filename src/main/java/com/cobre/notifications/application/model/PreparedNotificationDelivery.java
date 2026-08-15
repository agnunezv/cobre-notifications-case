package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.NotificationDestination;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record PreparedNotificationDelivery(
        @NotNull UUID attemptId,
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 64) String clientId,
        @NotBlank @Size(max = 128) String eventType,
        @NotBlank String content,
        @NotNull @Valid NotificationDestination destination,
        @Positive int deliveryCycle,
        @Positive int attemptNumber,
        @NotBlank @Size(max = 128) String correlationId,
        @NotNull Instant startedAt) {
}
