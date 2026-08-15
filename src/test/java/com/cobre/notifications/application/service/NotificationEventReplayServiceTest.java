package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.NotificationEventNotFoundException;
import com.cobre.notifications.application.model.NotificationEventReplayNotAllowedException;
import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import com.cobre.notifications.application.port.outbound.NotificationEventReplayRepository;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class NotificationEventReplayServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final ReplayNotificationEventCommand COMMAND =
            new ReplayNotificationEventCommand("CLIENT001", "EVT001");

    @Test
    void schedulesANewPendingCycleForAFailedEvent() {
        RecordingRepository repository = new RecordingRepository(DeliveryStatus.FAILED, true);

        service(repository).replay(COMMAND);

        assertThat(repository.command).isEqualTo(COMMAND);
        assertThat(repository.nextStatus).isEqualTo(DeliveryStatus.PENDING);
        assertThat(repository.replayedAt).isEqualTo(NOW);
    }

    @Test
    void reportsAnEventOutsideTheAuthenticatedClientAsNotFound() {
        RecordingRepository repository = new RecordingRepository(null, true);

        assertThatExceptionOfType(NotificationEventNotFoundException.class)
                .isThrownBy(() -> service(repository).replay(COMMAND))
                .withMessage("Notification event was not found");

        assertThat(repository.nextStatus).isNull();
    }

    @Test
    void rejectsReplayWhenTheDeliveryHasNotFailed() {
        RecordingRepository repository = new RecordingRepository(DeliveryStatus.COMPLETED, true);

        assertThatExceptionOfType(NotificationEventReplayNotAllowedException.class)
                .isThrownBy(() -> service(repository).replay(COMMAND))
                .withMessage("Only failed notification events can be replayed");

        assertThat(repository.nextStatus).isNull();
    }

    @Test
    void detectsAnUnexpectedPersistenceConflict() {
        RecordingRepository repository = new RecordingRepository(DeliveryStatus.FAILED, false);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service(repository).replay(COMMAND))
                .withMessage("The notification event changed while scheduling its replay");
    }

    private NotificationEventReplayService service(NotificationEventReplayRepository repository) {
        return new NotificationEventReplayService(
                repository,
                new DeliveryLifecycle(new RetryPolicy(1, List.of())),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RecordingRepository implements NotificationEventReplayRepository {

        private final DeliveryStatus currentStatus;
        private final boolean scheduled;
        private ReplayNotificationEventCommand command;
        private DeliveryStatus nextStatus;
        private Instant replayedAt;

        private RecordingRepository(DeliveryStatus currentStatus, boolean scheduled) {
            this.currentStatus = currentStatus;
            this.scheduled = scheduled;
        }

        @Override
        public Optional<DeliveryStatus> lockDeliveryStatus(ReplayNotificationEventCommand command) {
            return Optional.ofNullable(currentStatus);
        }

        @Override
        public boolean scheduleReplay(
                ReplayNotificationEventCommand command,
                DeliveryStatus nextStatus,
                Instant replayedAt) {
            this.command = command;
            this.nextStatus = nextStatus;
            this.replayedAt = replayedAt;
            return scheduled;
        }
    }
}
