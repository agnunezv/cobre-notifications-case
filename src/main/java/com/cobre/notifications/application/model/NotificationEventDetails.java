package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record NotificationEventDetails(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 128) String eventType,
        @NotBlank String content,
        @NotNull Instant createdAt,
        Instant deliveryDate,
        @NotNull DeliveryStatus deliveryStatus) {

    @AssertTrue(message = "deliveryDate is required when deliveryStatus is final") public boolean isDeliveryDateConsistent() {
        return deliveryStatus == null || !deliveryStatus.isFinal() || deliveryDate != null;
    }
}
