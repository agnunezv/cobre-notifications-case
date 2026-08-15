package com.cobre.notifications.application.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ClaimedNotificationDelivery(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 64) String clientId,
        @NotBlank @Size(max = 128) String eventType,
        @NotBlank String content,
        @Positive int deliveryCycle,
        @NotNull Instant leaseUntil) {
}
