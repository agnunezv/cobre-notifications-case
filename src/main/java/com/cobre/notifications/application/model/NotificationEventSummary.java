package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;

import java.time.Instant;

public record NotificationEventSummary(
        String eventId,
        String eventType,
        Instant createdAt,
        Instant deliveryDate,
        DeliveryStatus deliveryStatus) {
}
