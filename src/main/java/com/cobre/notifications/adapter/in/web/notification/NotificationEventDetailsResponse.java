package com.cobre.notifications.adapter.in.web.notification;

import com.cobre.notifications.application.model.NotificationEventDetails;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record NotificationEventDetailsResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        String content,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("delivery_date") Instant deliveryDate,
        @JsonProperty("delivery_status") DeliveryStatus deliveryStatus) {

    public static NotificationEventDetailsResponse from(NotificationEventDetails details) {
        return new NotificationEventDetailsResponse(
                details.eventId(),
                details.eventType(),
                details.content(),
                details.createdAt(),
                details.deliveryDate(),
                details.deliveryStatus());
    }
}
