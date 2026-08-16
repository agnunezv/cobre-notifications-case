package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;

public interface NotificationEventReplayRepository {

    Optional<DeliveryStatus> lockDeliveryStatus(@NotNull @Valid ReplayNotificationEventCommand command);

    boolean scheduleReplay(
            @NotNull @Valid ReplayNotificationEventCommand command,
            @NotNull DeliveryStatus nextStatus,
            @NotNull Instant replayedAt);
}
