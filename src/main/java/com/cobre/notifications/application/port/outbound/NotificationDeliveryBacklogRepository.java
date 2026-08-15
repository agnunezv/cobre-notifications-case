package com.cobre.notifications.application.port.outbound;

import jakarta.validation.constraints.NotNull;

import java.time.Duration;
import java.time.Instant;

public interface NotificationDeliveryBacklogRepository {

    long countDue(@NotNull Instant observedAt);

    Duration oldestDueAge(@NotNull Instant observedAt);
}
