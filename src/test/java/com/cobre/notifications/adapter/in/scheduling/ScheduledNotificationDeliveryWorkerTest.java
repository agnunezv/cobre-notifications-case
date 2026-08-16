package com.cobre.notifications.adapter.in.scheduling;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import com.cobre.notifications.config.NotificationDeliveryWorkerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ScheduledNotificationDeliveryWorkerTest {

    private static final NotificationDeliveryWorkerProperties PROPERTIES =
            new NotificationDeliveryWorkerProperties(
                    true,
                    "worker-1",
                    10,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    Duration.ofMinutes(2));

    @Test
    void delegatesEachScheduledExecutionToTheBatchUseCase() {
        AtomicReference<ClaimNotificationDeliveriesCommand> received = new AtomicReference<>();
        ProcessNotificationDeliveryBatchUseCase processBatch = command -> {
            received.set(command);
            return new NotificationDeliveryBatchResult(0, 2, 0, 2, 0, 0);
        };
        RecordingMetrics metrics = new RecordingMetrics();
        ScheduledNotificationDeliveryWorker worker = new ScheduledNotificationDeliveryWorker(
                processBatch,
                PROPERTIES,
                metrics);

        worker.processNextBatch();

        assertThat(received.get()).isEqualTo(PROPERTIES.claimCommand());
        assertThat(metrics.batchResult).isEqualTo(new NotificationDeliveryBatchResult(0, 2, 0, 2, 0, 0));
        assertThat(metrics.batchDuration).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    void keepsTheSchedulerAliveWhenAClaimCannotBeCreated() {
        ProcessNotificationDeliveryBatchUseCase processBatch = command -> {
            throw new IllegalStateException("PostgreSQL is unavailable");
        };
        RecordingMetrics metrics = new RecordingMetrics();
        ScheduledNotificationDeliveryWorker worker = new ScheduledNotificationDeliveryWorker(
                processBatch,
                PROPERTIES,
                metrics);

        assertThatCode(worker::processNextBatch).doesNotThrowAnyException();
        assertThat(metrics.batchFailureDuration).isNotNull().isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    void keepsTheSchedulerAliveWhenBatchMetricsCannotBeRecorded() {
        ProcessNotificationDeliveryBatchUseCase processBatch = command ->
                new NotificationDeliveryBatchResult(0, 0, 0, 0, 0, 0);
        NotificationDeliveryMetrics failingMetrics = new RecordingMetrics() {
            @Override
            public void recordBatch(NotificationDeliveryBatchResult result, Duration duration) {
                throw new IllegalStateException("Metrics unavailable");
            }
        };
        ScheduledNotificationDeliveryWorker worker = new ScheduledNotificationDeliveryWorker(
                processBatch,
                PROPERTIES,
                failingMetrics);

        assertThatCode(worker::processNextBatch).doesNotThrowAnyException();
    }

    private static class RecordingMetrics implements NotificationDeliveryMetrics {

        private NotificationDeliveryBatchResult batchResult;
        private Duration batchDuration;
        private Duration batchFailureDuration;

        @Override
        public void recordAttempt(
                PreparedNotificationDelivery delivery,
                WebhookDeliveryOutcome outcome) {
        }

        @Override
        public void recordBatch(NotificationDeliveryBatchResult result, Duration duration) {
            batchResult = result;
            batchDuration = duration;
        }

        @Override
        public void recordBatchFailure(Duration duration) {
            batchFailureDuration = duration;
        }
    }
}
