package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.NotificationDeliveryAttemptCompletion;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryCompletionRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationDeliveryCompletionService implements CompleteNotificationDeliveryAttemptUseCase {

    private final NotificationDeliveryCompletionRepository repository;
    private final DeliveryLifecycle deliveryLifecycle;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public NotificationDeliveryCompletionService(
            NotificationDeliveryCompletionRepository repository,
            DeliveryLifecycle deliveryLifecycle,
            RetryPolicy retryPolicy,
            Clock clock) {
        this.repository = repository;
        this.deliveryLifecycle = deliveryLifecycle;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean complete(PreparedNotificationDelivery delivery, WebhookDeliveryOutcome outcome) {
        Instant finishedAt = clock.instant();
        DeliveryStatus nextStatus =
                deliveryLifecycle.finishAttempt(DeliveryStatus.PROCESSING, outcome.result(), delivery.attemptNumber());
        Instant nextAttemptAt = nextAttemptAt(delivery.attemptNumber(), nextStatus, finishedAt);

        return repository.completeIfCurrent(
                new NotificationDeliveryAttemptCompletion(delivery, outcome, nextStatus, nextAttemptAt, finishedAt));
    }

    private Instant nextAttemptAt(int completedAttempts, DeliveryStatus nextStatus, Instant finishedAt) {
        if (nextStatus != DeliveryStatus.RETRY_SCHEDULED) {
            return null;
        }

        return finishedAt.plus(retryPolicy
                .retryDelayAfter(completedAttempts)
                .orElseThrow(() ->
                        new IllegalStateException("The retry policy did not provide a delay for a scheduled retry")));
    }
}
