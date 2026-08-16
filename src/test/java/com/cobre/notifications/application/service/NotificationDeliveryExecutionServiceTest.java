package com.cobre.notifications.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryGateway;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.NotificationDestination;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationDeliveryExecutionServiceTest {

    @Test
    void deliversThePreparedNotificationAndCompletesItsAttempt() {
        PreparedNotificationDelivery delivery = delivery();
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 202, null, null, 12);
        AtomicReference<PreparedNotificationDelivery> delivered = new AtomicReference<>();
        AtomicReference<WebhookDeliveryOutcome> completedWith = new AtomicReference<>();
        AtomicReference<WebhookDeliveryOutcome> measured = new AtomicReference<>();
        NotificationDeliveryGateway gateway = preparedDelivery -> {
            delivered.set(preparedDelivery);
            return outcome;
        };
        CompleteNotificationDeliveryAttemptUseCase completeAttempt = (preparedDelivery, result) -> {
            assertThat(preparedDelivery).isSameAs(delivery);
            completedWith.set(result);
            return true;
        };
        NotificationDeliveryExecutionService service =
                new NotificationDeliveryExecutionService(gateway, completeAttempt, metrics(measured));

        boolean completionApplied = service.deliver(delivery);

        assertThat(completionApplied).isTrue();
        assertThat(delivered.get()).isSameAs(delivery);
        assertThat(completedWith.get()).isSameAs(outcome);
        assertThat(measured.get()).isSameAs(outcome);
    }

    @Test
    void doesNotInterruptCompletionWhenAttemptMetricsFail() {
        PreparedNotificationDelivery delivery = delivery();
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, 200, null, null, 10);
        NotificationDeliveryMetrics failingMetrics = new NoOpMetrics() {
            @Override
            public void recordAttempt(
                    PreparedNotificationDelivery ignoredDelivery, WebhookDeliveryOutcome ignoredOutcome) {
                throw new IllegalStateException("Metrics unavailable");
            }
        };
        NotificationDeliveryExecutionService service = new NotificationDeliveryExecutionService(
                ignored -> outcome, (ignoredDelivery, ignoredOutcome) -> true, failingMetrics);

        assertThat(service.deliver(delivery)).isTrue();
    }

    private NotificationDeliveryMetrics metrics(AtomicReference<WebhookDeliveryOutcome> measured) {
        return new NoOpMetrics() {
            @Override
            public void recordAttempt(PreparedNotificationDelivery ignoredDelivery, WebhookDeliveryOutcome outcome) {
                measured.set(outcome);
            }
        };
    }

    private PreparedNotificationDelivery delivery() {
        UUID attemptId = UUID.randomUUID();
        return new PreparedNotificationDelivery(
                attemptId,
                "EVT001",
                "CLIENT001",
                "credit_payment",
                "Payment confirmed",
                new NotificationDestination("SUB001", URI.create("https://hooks.example.com/notifications")),
                1,
                1,
                attemptId.toString(),
                Instant.parse("2026-08-15T11:59:59Z"));
    }

    private static class NoOpMetrics implements NotificationDeliveryMetrics {

        @Override
        public void recordAttempt(PreparedNotificationDelivery delivery, WebhookDeliveryOutcome outcome) {}

        @Override
        public void recordBatch(NotificationDeliveryBatchResult result, Duration duration) {}

        @Override
        public void recordBatchFailure(Duration duration) {}
    }
}
