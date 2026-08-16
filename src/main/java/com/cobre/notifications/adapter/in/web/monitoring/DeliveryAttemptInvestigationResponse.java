package com.cobre.notifications.adapter.in.web.monitoring;

import com.cobre.notifications.application.model.NotificationDeliveryAttemptDetails;
import com.cobre.notifications.domain.model.DeliveryAttemptOrigin;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptInvestigationResponse(
        @JsonProperty("attempt_id") UUID attemptId,
        @JsonProperty("delivery_cycle") int deliveryCycle,
        @JsonProperty("attempt_number") int attemptNumber,
        DeliveryAttemptOrigin origin,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("finished_at") Instant finishedAt,
        DeliveryAttemptResult result,
        @JsonProperty("http_status") Integer httpStatus,
        @JsonProperty("failure_category") String failureCategory,
        @JsonProperty("failure_description") String failureDescription,
        @JsonProperty("latency_ms") Long latencyMs,
        @JsonProperty("correlation_id") String correlationId) {

    public static DeliveryAttemptInvestigationResponse from(NotificationDeliveryAttemptDetails attempt) {
        return new DeliveryAttemptInvestigationResponse(
                attempt.attemptId(),
                attempt.deliveryCycle(),
                attempt.attemptNumber(),
                attempt.origin(),
                attempt.startedAt(),
                attempt.finishedAt(),
                attempt.result(),
                attempt.httpStatus(),
                attempt.failureCategory(),
                attempt.failureDescription(),
                attempt.latencyMs(),
                attempt.correlationId());
    }
}
