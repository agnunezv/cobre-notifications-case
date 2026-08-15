package com.cobre.notifications.adapter.in.scheduling;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
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
        ScheduledNotificationDeliveryWorker worker = new ScheduledNotificationDeliveryWorker(
                processBatch,
                PROPERTIES);

        worker.processNextBatch();

        assertThat(received.get()).isEqualTo(PROPERTIES.claimCommand());
    }

    @Test
    void keepsTheSchedulerAliveWhenAClaimCannotBeCreated() {
        ProcessNotificationDeliveryBatchUseCase processBatch = command -> {
            throw new IllegalStateException("PostgreSQL is unavailable");
        };
        ScheduledNotificationDeliveryWorker worker = new ScheduledNotificationDeliveryWorker(
                processBatch,
                PROPERTIES);

        assertThatCode(worker::processNextBatch).doesNotThrowAnyException();
    }
}
