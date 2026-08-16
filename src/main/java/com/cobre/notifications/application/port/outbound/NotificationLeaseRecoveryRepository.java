package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.ExpiredNotificationLease;
import com.cobre.notifications.application.model.NotificationLeaseRecovery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public interface NotificationLeaseRecoveryRepository {

    List<@Valid ExpiredNotificationLease> lockExpired(@NotNull Instant expiredAt, int batchSize);

    void recover(@NotNull @Valid NotificationLeaseRecovery recovery);
}
