package com.cobre.notifications.adapter.in.web.monitoring;

import com.cobre.notifications.application.model.NotificationDeliveryInvestigation;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record NotificationDeliveryInvestigationResponse(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("client_id") String clientId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("delivery_status") DeliveryStatus deliveryStatus,
        @JsonProperty("delivery_cycle") int deliveryCycle,
        @JsonProperty("next_attempt_at") Instant nextAttemptAt,
        @JsonProperty("delivery_date") Instant deliveryDate,
        @JsonProperty("webhook_delivered_at") Instant webhookDeliveredAt,
        @JsonProperty("subscription_id") String subscriptionId,
        @JsonProperty("attempt_history_complete") boolean attemptHistoryComplete,
        @JsonProperty("updated_at") Instant updatedAt,
        List<DeliveryAttemptInvestigationResponse> attempts) {

    public static NotificationDeliveryInvestigationResponse from(
            NotificationDeliveryInvestigation investigation) {
        return new NotificationDeliveryInvestigationResponse(
                investigation.eventId(),
                investigation.clientId(),
                investigation.eventType(),
                investigation.createdAt(),
                investigation.deliveryStatus(),
                investigation.deliveryCycle(),
                investigation.nextAttemptAt(),
                investigation.deliveryDate(),
                investigation.webhookDeliveredAt(),
                investigation.subscriptionId(),
                investigation.attemptHistoryComplete(),
                investigation.updatedAt(),
                investigation.attempts().stream()
                        .map(DeliveryAttemptInvestigationResponse::from)
                        .toList());
    }
}
