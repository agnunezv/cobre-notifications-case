package com.cobre.notifications.adapter.out.observability;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.NotificationDestination;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerNotificationDeliveryMetricsTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Test
    void recordsAttemptResultsDurationsAndBoundedFailureTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNotificationDeliveryMetrics metrics =
                metrics(registry, false);

        metrics.recordAttempt(delivery(), new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                null,
                NotificationDeliveryFailureCategory.TIMEOUT,
                "The webhook request timed out",
                125));

        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.ATTEMPT_COUNT)
                .tags(
                        "client_id", "CLIENT001",
                        "event_type", "credit_payment",
                        "result", "retryable_failure",
                        "failure_category", "timeout",
                        "http_status_class", "none")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.ATTEMPT_DURATION)
                .tags(
                        "client_id", "CLIENT001",
                        "event_type", "credit_payment",
                        "result", "retryable_failure",
                        "failure_category", "timeout",
                        "http_status_class", "none")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(125);

        metrics.recordAttempt(delivery(), new WebhookDeliveryOutcome(
                DeliveryAttemptResult.SUCCESS,
                202,
                null,
                null,
                40));

        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.ATTEMPT_COUNT)
                .tags(
                        "client_id", "CLIENT001",
                        "event_type", "credit_payment",
                        "result", "success",
                        "failure_category", "none",
                        "http_status_class", "2xx")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void recordsWorkerOutcomesAndSuccessfulOrFailedBatchDurations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNotificationDeliveryMetrics metrics =
                metrics(registry, true);

        metrics.recordBatch(
                new NotificationDeliveryBatchResult(2, 5, 1, 1, 1, 2),
                Duration.ofMillis(250));
        metrics.recordBatchFailure(Duration.ofMillis(50));

        assertThat(workerEventCount(registry, "lease_recovered")).isEqualTo(2);
        assertThat(workerEventCount(registry, "claimed")).isEqualTo(5);
        assertThat(workerEventCount(registry, "processing_failure")).isEqualTo(2);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.BATCH_DURATION)
                .tag("outcome", "success")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.BATCH_DURATION)
                .tag("outcome", "failure")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(50);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_POLL_COUNT)
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_POLL_COUNT)
                .tag("outcome", "failure")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_ENABLED)
                .gauge()
                .value()).isEqualTo(1);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_LAST_SUCCESS)
                .gauge()
                .value()).isEqualTo(NOW.getEpochSecond());
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_LAST_FAILURE)
                .gauge()
                .value()).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    void exposesDisabledWorkerWithoutReportingAFalseHeartbeat() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        metrics(registry, false);

        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_ENABLED)
                .gauge()
                .value()).isZero();
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.WORKER_LAST_SUCCESS)
                .gauge()
                .value()).isZero();
    }

    private MicrometerNotificationDeliveryMetrics metrics(
            SimpleMeterRegistry registry,
            boolean workerEnabled) {
        return new MicrometerNotificationDeliveryMetrics(
                registry,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new com.cobre.notifications.config.NotificationDeliveryWorkerProperties(
                        workerEnabled,
                        "test-worker",
                        10,
                        Duration.ofSeconds(1),
                        Duration.ZERO,
                        Duration.ofMinutes(2)));
    }

    private double workerEventCount(SimpleMeterRegistry registry, String activity) {
        return registry.get(MicrometerNotificationDeliveryMetrics.WORKER_EVENT_COUNT)
                .tag("activity", activity)
                .counter()
                .count();
    }

    private PreparedNotificationDelivery delivery() {
        UUID attemptId = UUID.randomUUID();
        return new PreparedNotificationDelivery(
                attemptId,
                "EVT001",
                "CLIENT001",
                "credit_payment",
                "Payment confirmed",
                new NotificationDestination(
                        "SUB001",
                        URI.create("https://hooks.example.com/notifications")),
                1,
                1,
                attemptId.toString(),
                Instant.parse("2026-08-15T11:59:59Z"));
    }
}
