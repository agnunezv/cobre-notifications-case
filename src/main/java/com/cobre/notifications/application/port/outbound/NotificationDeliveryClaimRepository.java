package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import java.time.Instant;
import java.util.List;

public interface NotificationDeliveryClaimRepository {

    List<ClaimedNotificationDelivery> claimDue(String workerId, Instant claimedAt, Instant leaseUntil, int batchSize);
}
