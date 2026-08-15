package com.cobre.notifications.domain.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record NotificationEvent(
        @NotBlank String eventId,
        @NotBlank String clientId,
        @NotBlank String eventType,
        @NotBlank String content,
        @NotNull Instant createdAt,
        Instant deliveryDate,
        @NotNull DeliveryStatus deliveryStatus) {

    @AssertTrue(message = "deliveryDate is required when deliveryStatus is final")
    public boolean isDeliveryDateConsistent() {
        return deliveryStatus == null || !deliveryStatus.isFinal() || deliveryDate != null;
    }
}
