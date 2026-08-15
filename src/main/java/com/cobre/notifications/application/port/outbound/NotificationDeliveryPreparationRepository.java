package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.DeliveryPreparationFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.domain.model.NotificationDestination;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryPreparationRepository {

    Optional<PreparedNotificationDelivery> prepare(
            ClaimedNotificationDelivery claimedDelivery,
            NotificationDestination destination,
            UUID attemptId,
            Instant startedAt);

    void failConfigurationIfClaimIsCurrent(
            ClaimedNotificationDelivery claimedDelivery,
            DeliveryPreparationFailureCategory failureCategory,
            UUID attemptId,
            Instant finishedAt);
}
