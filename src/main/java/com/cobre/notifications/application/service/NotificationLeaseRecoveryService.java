package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.ExpiredNotificationLease;
import com.cobre.notifications.application.model.NotificationLeaseRecovery;
import com.cobre.notifications.application.port.inbound.RecoverExpiredNotificationLeasesUseCase;
import com.cobre.notifications.application.port.outbound.NotificationLeaseRecoveryRepository;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationLeaseRecoveryService implements RecoverExpiredNotificationLeasesUseCase {

    private final NotificationLeaseRecoveryRepository repository;
    private final DeliveryLifecycle deliveryLifecycle;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public NotificationLeaseRecoveryService(
            NotificationLeaseRecoveryRepository repository,
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
    public int recoverExpired(int batchSize) {
        Instant recoveredAt = clock.instant();
        List<ExpiredNotificationLease> expiredLeases = repository.lockExpired(recoveredAt, batchSize);

        expiredLeases.stream()
                .map(expiredLease -> recoveryFor(expiredLease, recoveredAt))
                .forEach(repository::recover);

        return expiredLeases.size();
    }

    private NotificationLeaseRecovery recoveryFor(ExpiredNotificationLease expiredLease, Instant recoveredAt) {
        if (!expiredLease.hasOpenAttempt()) {
            return new NotificationLeaseRecovery(
                    expiredLease, DeliveryStatus.RETRY_SCHEDULED, recoveredAt, recoveredAt);
        }

        int completedAttempts = expiredLease.openAttemptNumber();
        DeliveryStatus nextStatus = deliveryLifecycle.finishAttempt(
                DeliveryStatus.PROCESSING, DeliveryAttemptResult.RETRYABLE_FAILURE, completedAttempts);
        Instant nextAttemptAt = nextStatus == DeliveryStatus.RETRY_SCHEDULED
                ? recoveredAt.plus(retryPolicy
                        .retryDelayAfter(completedAttempts)
                        .orElseThrow(() -> new IllegalStateException(
                                "The retry policy did not provide a delay for lease recovery")))
                : null;

        return new NotificationLeaseRecovery(expiredLease, nextStatus, nextAttemptAt, recoveredAt);
    }
}
