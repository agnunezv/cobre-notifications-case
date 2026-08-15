package com.cobre.notifications.adapter.in.scheduling;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import com.cobre.notifications.config.NotificationDeliveryWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

public class ScheduledNotificationDeliveryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledNotificationDeliveryWorker.class);

    private final ProcessNotificationDeliveryBatchUseCase processBatch;
    private final NotificationDeliveryWorkerProperties properties;
    private final NotificationDeliveryMetrics metrics;

    public ScheduledNotificationDeliveryWorker(
            ProcessNotificationDeliveryBatchUseCase processBatch,
            NotificationDeliveryWorkerProperties properties,
            NotificationDeliveryMetrics metrics) {
        this.processBatch = processBatch;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Scheduled(
            fixedDelayString = "${notifications.delivery.worker.poll-interval}",
            initialDelayString = "${notifications.delivery.worker.initial-delay}")
    public void processNextBatch() {
        long startedAt = System.nanoTime();
        try {
            NotificationDeliveryBatchResult result = processBatch.process(properties.claimCommand());
            recordBatch(result, elapsedSince(startedAt));
            if (result.recoveredLeaseCount() > 0 || result.claimedCount() > 0) {
                LOGGER.info(
                        "Notification delivery batch processed by {}: leasesRecovered={}, claimed={}, "
                                + "preparationSkipped={}, completionApplied={}, staleCompletions={}, "
                                + "processingFailures={}",
                        properties.workerId(),
                        result.recoveredLeaseCount(),
                        result.claimedCount(),
                        result.preparationSkippedCount(),
                        result.completionAppliedCount(),
                        result.staleCompletionCount(),
                        result.processingFailureCount());
            }
        } catch (RuntimeException exception) {
            recordBatchFailure(elapsedSince(startedAt));
            LOGGER.error(
                    "Notification delivery worker {} could not process its next batch",
                    properties.workerId(),
                    exception);
        }
    }

    private void recordBatch(NotificationDeliveryBatchResult result, Duration duration) {
        try {
            metrics.recordBatch(result, duration);
        } catch (RuntimeException exception) {
            LOGGER.warn("Notification delivery batch metrics could not be recorded", exception);
        }
    }

    private void recordBatchFailure(Duration duration) {
        try {
            metrics.recordBatchFailure(duration);
        } catch (RuntimeException exception) {
            LOGGER.warn("Notification delivery batch failure metrics could not be recorded", exception);
        }
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAt));
    }
}
