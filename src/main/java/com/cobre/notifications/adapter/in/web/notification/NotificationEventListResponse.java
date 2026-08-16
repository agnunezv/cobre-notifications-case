package com.cobre.notifications.adapter.in.web.notification;

import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record NotificationEventListResponse(
        List<Item> items,
        int page,
        int size,
        @JsonProperty("has_next") boolean hasNext) {

    public NotificationEventListResponse {
        if (items != null) {
            items = List.copyOf(items);
        }
    }

    public static NotificationEventListResponse from(NotificationEventPage page) {
        List<Item> items = page.items().stream()
                .map(summary -> new Item(
                        summary.eventId(),
                        summary.eventType(),
                        summary.createdAt(),
                        summary.deliveryDate(),
                        summary.deliveryStatus()))
                .toList();
        return new NotificationEventListResponse(items, page.page(), page.size(), page.hasNext());
    }

    public record Item(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("delivery_date") Instant deliveryDate,
            @JsonProperty("delivery_status") DeliveryStatus deliveryStatus) {}
}
