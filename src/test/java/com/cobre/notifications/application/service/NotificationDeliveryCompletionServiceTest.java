package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.NotificationDeliveryAttemptCompletion;
import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryCompletionRepository;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationDestination;
import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeliveryCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final RetryPolicy RETRY_POLICY = new RetryPolicy(
            4,
            List.of(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(30)));

    @Test
    void schedulesTheConfiguredRetryAfterARetryableFailure() {
        AtomicReference<NotificationDeliveryAttemptCompletion> recorded = new AtomicReference<>();
        NotificationDeliveryCompletionService service = service(completion -> {
            recorded.set(completion);
            return true;
        });

        boolean completed = service.complete(
                delivery(2),
                new WebhookDeliveryOutcome(
                        DeliveryAttemptResult.RETRYABLE_FAILURE,
                        503,
                        NotificationDeliveryFailureCategory.HTTP_RESPONSE,
                        "The webhook endpoint returned HTTP 503",
                        20));

        assertThat(completed).isTrue();
        assertThat(recorded.get().nextStatus()).isEqualTo(DeliveryStatus.RETRY_SCHEDULED);
        assertThat(recorded.get().nextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(recorded.get().finishedAt()).isEqualTo(NOW);
    }

    @Test
    void failsAfterTheLastConfiguredAttempt() {
        AtomicReference<NotificationDeliveryAttemptCompletion> recorded = new AtomicReference<>();
        NotificationDeliveryCompletionService service = service(completion -> {
            recorded.set(completion);
            return true;
        });

        service.complete(
                delivery(4),
                new WebhookDeliveryOutcome(
                        DeliveryAttemptResult.RETRYABLE_FAILURE,
                        null,
                        NotificationDeliveryFailureCategory.TIMEOUT,
                        "The webhook request timed out",
                        5000));

        assertThat(recorded.get().nextStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(recorded.get().nextAttemptAt()).isNull();
    }

    @Test
    void propagatesThatALateCompletionWasNotApplied() {
        NotificationDeliveryCompletionService service = service(completion -> false);

        boolean completed = service.complete(
                delivery(1),
                new WebhookDeliveryOutcome(
                        DeliveryAttemptResult.SUCCESS,
                        204,
                        null,
                        null,
                        10));

        assertThat(completed).isFalse();
    }

    private NotificationDeliveryCompletionService service(
            NotificationDeliveryCompletionRepository repository) {
        return new NotificationDeliveryCompletionService(
                repository,
                new DeliveryLifecycle(RETRY_POLICY),
                RETRY_POLICY,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PreparedNotificationDelivery delivery(int attemptNumber) {
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
                attemptNumber,
                attemptId.toString(),
                NOW.minusSeconds(1));
    }
}
