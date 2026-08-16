package com.cobre.notifications.adapter.out.observability;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import com.cobre.notifications.config.NotificationDeliveryWorkerProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Validated
public class MicrometerNotificationDeliveryMetrics implements NotificationDeliveryMetrics {

    static final String ATTEMPT_COUNT = "cobre.notifications.delivery.attempts";
    static final String ATTEMPT_DURATION = "cobre.notifications.delivery.attempt.duration";
    static final String WORKER_EVENT_COUNT = "cobre.notifications.worker.events";
    static final String BATCH_DURATION = "cobre.notifications.worker.batch.duration";
    static final String WORKER_POLL_COUNT = "cobre.notifications.worker.polls";
    static final String WORKER_ENABLED = "cobre.notifications.worker.enabled";
    static final String WORKER_LAST_SUCCESS = "cobre.notifications.worker.last.success";
    static final String WORKER_LAST_FAILURE = "cobre.notifications.worker.last.failure";

    private static final String NONE = "none";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final AtomicLong lastSuccessfulPollEpochSeconds = new AtomicLong();
    private final AtomicLong lastFailedPollEpochSeconds = new AtomicLong();

    public MicrometerNotificationDeliveryMetrics(
            MeterRegistry meterRegistry,
            Clock clock,
            NotificationDeliveryWorkerProperties workerProperties) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;

        Gauge.builder(WORKER_ENABLED, () -> workerProperties.enabled() ? 1 : 0)
                .description("Whether notification delivery polling is enabled in this application instance")
                .register(meterRegistry);
        Gauge.builder(WORKER_LAST_SUCCESS, lastSuccessfulPollEpochSeconds, AtomicLong::doubleValue)
                .description("Unix time of the last successful notification worker poll")
                .baseUnit("seconds")
                .register(meterRegistry);
        Gauge.builder(WORKER_LAST_FAILURE, lastFailedPollEpochSeconds, AtomicLong::doubleValue)
                .description("Unix time of the last failed notification worker poll")
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Override
    public void recordAttempt(
            PreparedNotificationDelivery delivery,
            WebhookDeliveryOutcome outcome) {
        String result = normalized(outcome.result());
        String failureCategory = outcome.failureCategory() == null
                ? NONE
                : normalized(outcome.failureCategory());
        String httpStatusClass = httpStatusClass(outcome.httpStatus());

        Counter.builder(ATTEMPT_COUNT)
                .description("Webhook delivery attempts by result and failure category")
                .tag("client_id", delivery.clientId())
                .tag("event_type", delivery.eventType())
                .tag("result", result)
                .tag("failure_category", failureCategory)
                .tag("http_status_class", httpStatusClass)
                .register(meterRegistry)
                .increment();
        Timer.builder(ATTEMPT_DURATION)
                .description("Webhook delivery attempt duration")
                .tag("client_id", delivery.clientId())
                .tag("event_type", delivery.eventType())
                .tag("result", result)
                .tag("failure_category", failureCategory)
                .tag("http_status_class", httpStatusClass)
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
        recordWorkerPoll("success");
        lastSuccessfulPollEpochSeconds.set(clock.instant().getEpochSecond());
        recordBatchDuration("success", duration);
    }

    @Override
    public void recordBatchFailure(Duration duration) {
        recordWorkerPoll("failure");
        lastFailedPollEpochSeconds.set(clock.instant().getEpochSecond());
        recordBatchDuration("failure", duration);
    }

    private void recordWorkerPoll(String outcome) {
        Counter.builder(WORKER_POLL_COUNT)
                .description("Notification delivery worker polling outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
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

    private String httpStatusClass(Integer httpStatus) {
        if (httpStatus == null) {
            return NONE;
        }
        int statusClass = httpStatus / 100;
        return statusClass >= 1 && statusClass <= 5
                ? statusClass + "xx"
                : "other";
    }
}
