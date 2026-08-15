package com.cobre.notifications.adapter.in.scheduling;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import com.cobre.notifications.config.NotificationDeliveryWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ScheduledNotificationDeliveryWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledNotificationDeliveryWorker.class);

    private final ProcessNotificationDeliveryBatchUseCase processBatch;
    private final NotificationDeliveryWorkerProperties properties;

    public ScheduledNotificationDeliveryWorker(
            ProcessNotificationDeliveryBatchUseCase processBatch,
            NotificationDeliveryWorkerProperties properties) {
        this.processBatch = processBatch;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${notifications.delivery.worker.poll-interval}",
            initialDelayString = "${notifications.delivery.worker.initial-delay}")
    public void processNextBatch() {
        try {
            NotificationDeliveryBatchResult result = processBatch.process(properties.claimCommand());
            if (result.claimedCount() > 0) {
                LOGGER.info(
                        "Notification delivery batch processed by {}: claimed={}, preparationSkipped={}, "
                                + "completionApplied={}, staleCompletions={}, processingFailures={}",
                        properties.workerId(),
                        result.claimedCount(),
                        result.preparationSkippedCount(),
                        result.completionAppliedCount(),
                        result.staleCompletionCount(),
                        result.processingFailureCount());
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Notification delivery worker {} could not process its next batch",
                    properties.workerId(),
                    exception);
        }
    }
}
