package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryGateway;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.NotificationDestination;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryExecutionServiceTest {

    @Test
    void deliversThePreparedNotificationAndCompletesItsAttempt() {
        PreparedNotificationDelivery delivery = delivery();
        WebhookDeliveryOutcome outcome = new WebhookDeliveryOutcome(
                DeliveryAttemptResult.SUCCESS,
                202,
                null,
                null,
                12);
        AtomicReference<PreparedNotificationDelivery> delivered = new AtomicReference<>();
        AtomicReference<WebhookDeliveryOutcome> completedWith = new AtomicReference<>();
        NotificationDeliveryGateway gateway = preparedDelivery -> {
            delivered.set(preparedDelivery);
            return outcome;
        };
        CompleteNotificationDeliveryAttemptUseCase completeAttempt = (preparedDelivery, result) -> {
            assertThat(preparedDelivery).isSameAs(delivery);
            completedWith.set(result);
            return true;
        };
        NotificationDeliveryExecutionService service = new NotificationDeliveryExecutionService(
                gateway,
                completeAttempt);

        boolean completionApplied = service.deliver(delivery);

        assertThat(completionApplied).isTrue();
        assertThat(delivered.get()).isSameAs(delivery);
        assertThat(completedWith.get()).isSameAs(outcome);
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
