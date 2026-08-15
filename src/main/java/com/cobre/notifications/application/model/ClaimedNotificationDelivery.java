package com.cobre.notifications.application.model;

import java.time.Instant;

public record ClaimedNotificationDelivery(
        String eventId,
        String clientId,
        String eventType,
        String content,
        int deliveryCycle,
        Instant leaseUntil) {
}
