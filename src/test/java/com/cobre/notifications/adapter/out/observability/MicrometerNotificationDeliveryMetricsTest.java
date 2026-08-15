package com.cobre.notifications.adapter.out.observability;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerNotificationDeliveryMetricsTest {

    @Test
    void recordsAttemptResultsDurationsAndBoundedFailureTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNotificationDeliveryMetrics metrics =
                new MicrometerNotificationDeliveryMetrics(registry);

        metrics.recordAttempt(new WebhookDeliveryOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                null,
                NotificationDeliveryFailureCategory.TIMEOUT,
                "The webhook request timed out",
                125));

        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.ATTEMPT_COUNT)
                .tags("result", "retryable_failure", "failure_category", "timeout")
                .counter()
                .count()).isEqualTo(1);
        assertThat(registry.get(MicrometerNotificationDeliveryMetrics.ATTEMPT_DURATION)
                .tags("result", "retryable_failure", "failure_category", "timeout")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(125);
    }

    @Test
    void recordsWorkerOutcomesAndSuccessfulOrFailedBatchDurations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerNotificationDeliveryMetrics metrics =
                new MicrometerNotificationDeliveryMetrics(registry);

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
    }

    private double workerEventCount(SimpleMeterRegistry registry, String activity) {
        return registry.get(MicrometerNotificationDeliveryMetrics.WORKER_EVENT_COUNT)
                .tag("activity", activity)
                .counter()
                .count();
    }
}
