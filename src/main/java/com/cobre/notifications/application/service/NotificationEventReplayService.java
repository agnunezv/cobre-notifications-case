package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.NotificationEventNotFoundException;
import com.cobre.notifications.application.model.NotificationEventReplayNotAllowedException;
import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.port.inbound.ReplayNotificationEventUseCase;
import com.cobre.notifications.application.port.outbound.NotificationEventReplayRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;

@Service
@Validated
public class NotificationEventReplayService implements ReplayNotificationEventUseCase {

    private final NotificationEventReplayRepository repository;
    private final DeliveryLifecycle deliveryLifecycle;
    private final Clock clock;

    public NotificationEventReplayService(
            NotificationEventReplayRepository repository,
            DeliveryLifecycle deliveryLifecycle,
            Clock clock) {
        this.repository = repository;
        this.deliveryLifecycle = deliveryLifecycle;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void replay(ReplayNotificationEventCommand command) {
        DeliveryStatus currentStatus = repository.lockDeliveryStatus(command)
                .orElseThrow(NotificationEventNotFoundException::new);
        DeliveryStatus nextStatus = replayStatus(currentStatus);
        Instant replayedAt = clock.instant();

        if (!repository.scheduleReplay(command, nextStatus, replayedAt)) {
            throw new IllegalStateException("The notification event changed while scheduling its replay");
        }
    }

    private DeliveryStatus replayStatus(DeliveryStatus currentStatus) {
        try {
            return deliveryLifecycle.replay(currentStatus);
        } catch (IllegalStateException exception) {
            throw new NotificationEventReplayNotAllowedException(exception);
        }
    }
}
