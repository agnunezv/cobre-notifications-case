package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record NotificationDeliveryInvestigation(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 64) String clientId,
        @NotBlank @Size(max = 128) String eventType,
        @NotNull Instant createdAt,
        @NotNull DeliveryStatus deliveryStatus,
        @Positive int deliveryCycle,
        Instant nextAttemptAt,
        Instant deliveryDate,
        Instant webhookDeliveredAt,
        @Size(max = 64) String subscriptionId,
        boolean attemptHistoryComplete,
        @NotNull Instant updatedAt,
        @NotNull List<@NotNull @Valid NotificationDeliveryAttemptDetails> attempts) {

    public NotificationDeliveryInvestigation {
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }
}
