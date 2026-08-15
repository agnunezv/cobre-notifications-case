package com.cobre.notifications.adapter.in.bootstrap;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record NotificationEventJsonRecord(
        @NotBlank @JsonProperty("event_id") String eventId,
        @NotBlank @JsonProperty("event_type") String eventType,
        @NotBlank String content,
        @NotNull @JsonProperty("delivery_date") Instant deliveryDate,
        @NotBlank
        @Pattern(
                regexp = "(?i)pending|processing|retry_scheduled|completed|failed",
                message = "must be a supported delivery status")
        @JsonProperty("delivery_status") String deliveryStatus,
        @NotBlank @JsonProperty("client_id") String clientId) {
}
