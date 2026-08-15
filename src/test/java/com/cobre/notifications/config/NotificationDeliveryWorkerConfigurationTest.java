package com.cobre.notifications.config;

import com.cobre.notifications.adapter.in.scheduling.ScheduledNotificationDeliveryWorker;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryWorkerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationDeliveryWorkerConfiguration.class)
            .withBean(
                    ProcessNotificationDeliveryBatchUseCase.class,
                    () -> command -> new NotificationDeliveryBatchResult(0, 0, 0, 0, 0, 0))
            .withBean(NotificationDeliveryMetrics.class, NoOpMetrics::new);

    @Test
    void keepsScheduledDeliveryDisabledUnlessExplicitlyEnabled() {
        contextRunner
                .withPropertyValues(workerProperties(false))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ScheduledNotificationDeliveryWorker.class);
                });
    }

    @Test
    void createsTheScheduledWorkerAndBindsHumanReadableDurationsWhenEnabled() {
        contextRunner
                .withPropertyValues(workerProperties(true))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ScheduledNotificationDeliveryWorker.class);

                    NotificationDeliveryWorkerProperties properties = context.getBean(
                            NotificationDeliveryWorkerProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.pollInterval()).isEqualTo(java.time.Duration.ofMillis(250));
                    assertThat(properties.initialDelay()).isEqualTo(java.time.Duration.ofHours(1));
                    assertThat(properties.leaseDuration()).isEqualTo(java.time.Duration.ofMinutes(2));
                });
    }

    private String[] workerProperties(boolean enabled) {
        return new String[]{
                "notifications.delivery.worker.enabled=" + enabled,
                "notifications.delivery.worker.worker-id=worker-1",
                "notifications.delivery.worker.batch-size=10",
                "notifications.delivery.worker.poll-interval=250ms",
                "notifications.delivery.worker.initial-delay=1h",
                "notifications.delivery.worker.lease-duration=2m"
        };
    }

    private static final class NoOpMetrics implements NotificationDeliveryMetrics {

        @Override
        public void recordAttempt(WebhookDeliveryOutcome outcome) {
        }

        @Override
        public void recordBatch(NotificationDeliveryBatchResult result, Duration duration) {
        }

        @Override
        public void recordBatchFailure(Duration duration) {
        }
    }
}
