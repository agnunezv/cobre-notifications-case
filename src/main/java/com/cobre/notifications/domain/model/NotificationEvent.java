package com.cobre.notifications.domain.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record NotificationEvent(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 64) String clientId,
        @NotBlank @Size(max = 128) String eventType,
        @NotBlank String content,
        @NotNull Instant createdAt,
        Instant deliveryDate,
        @NotNull DeliveryStatus deliveryStatus) {

    @AssertTrue(message = "deliveryDate is required when deliveryStatus is final") public boolean isDeliveryDateConsistent() {
        return deliveryStatus == null || !deliveryStatus.isFinal() || deliveryDate != null;
    }
}
