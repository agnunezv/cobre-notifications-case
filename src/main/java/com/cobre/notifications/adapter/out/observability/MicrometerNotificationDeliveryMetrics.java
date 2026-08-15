package com.cobre.notifications.adapter.out.observability;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Locale;

@Component
@Validated
public class MicrometerNotificationDeliveryMetrics implements NotificationDeliveryMetrics {

    static final String ATTEMPT_COUNT = "cobre.notifications.delivery.attempts";
    static final String ATTEMPT_DURATION = "cobre.notifications.delivery.attempt.duration";
    static final String WORKER_EVENT_COUNT = "cobre.notifications.worker.events";
    static final String BATCH_DURATION = "cobre.notifications.worker.batch.duration";

    private static final String NONE = "none";

    private final MeterRegistry meterRegistry;

    public MicrometerNotificationDeliveryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordAttempt(WebhookDeliveryOutcome outcome) {
        String result = normalized(outcome.result());
        String failureCategory = outcome.failureCategory() == null
                ? NONE
                : normalized(outcome.failureCategory());

        Counter.builder(ATTEMPT_COUNT)
                .description("Webhook delivery attempts by result and failure category")
                .tag("result", result)
                .tag("failure_category", failureCategory)
                .register(meterRegistry)
                .increment();
        Timer.builder(ATTEMPT_DURATION)
                .description("Webhook delivery attempt duration")
                .tag("result", result)
                .tag("failure_category", failureCategory)
                .register(meterRegistry)
                .record(Duration.ofMillis(outcome.latencyMs()));
    }

    @Override
    public void recordBatch(NotificationDeliveryBatchResult result, Duration duration) {
        recordWorkerEvents("lease_recovered", result.recoveredLeaseCount());
        recordWorkerEvents("claimed", result.claimedCount());
        recordWorkerEvents("preparation_skipped", result.preparationSkippedCount());
        recordWorkerEvents("completion_applied", result.completionAppliedCount());
        recordWorkerEvents("stale_completion", result.staleCompletionCount());
        recordWorkerEvents("processing_failure", result.processingFailureCount());
        recordBatchDuration("success", duration);
    }

    @Override
    public void recordBatchFailure(Duration duration) {
        recordBatchDuration("failure", duration);
    }

    private void recordWorkerEvents(String activity, int count) {
        if (count == 0) {
            return;
        }
        Counter.builder(WORKER_EVENT_COUNT)
                .description("Notification event activity observed while processing worker batches")
                .tag("activity", activity)
                .register(meterRegistry)
                .increment(count);
    }

    private void recordBatchDuration(String outcome, Duration duration) {
        Timer.builder(BATCH_DURATION)
                .description("Notification worker batch duration")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(duration);
    }

    private String normalized(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
