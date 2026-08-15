package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;

import java.time.Instant;

public record NotificationEventQuery(
        String clientId,
        Instant createdFrom,
        Instant createdTo,
        DeliveryStatus deliveryStatus,
        int page,
        int size) {

    public static final int MAX_PAGE_SIZE = 100;

    public NotificationEventQuery {
        if (clientId == null || clientId.isBlank()) {
            throw new InvalidNotificationEventQueryException("An authenticated client is required");
        }
        if (page < 0) {
            throw new InvalidNotificationEventQueryException("page must be greater than or equal to zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidNotificationEventQueryException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw new InvalidNotificationEventQueryException("created_from must be earlier than created_to");
        }
    }
}
