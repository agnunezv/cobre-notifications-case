package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.inbound.DeliverPreparedNotificationUseCase;
import com.cobre.notifications.application.port.inbound.PrepareNotificationDeliveryUseCase;
import com.cobre.notifications.domain.model.NotificationDestination;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class NotificationDeliveryBatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private static final ClaimNotificationDeliveriesCommand COMMAND =
            new ClaimNotificationDeliveriesCommand("worker-1", 5, Duration.ofSeconds(30));

    @Test
    void isolatesPerDeliveryFailuresAndReportsEveryBatchOutcome() {
        List<ClaimedNotificationDelivery> claimed = List.of(
                claimed("FINALIZED"),
                claimed("SKIPPED"),
                claimed("STALE"),
                claimed("PREPARATION_FAILURE"),
                claimed("DELIVERY_FAILURE"));
        ClaimNotificationDeliveriesUseCase claimUseCase = command -> claimed;
        PrepareNotificationDeliveryUseCase prepareUseCase = delivery -> switch (delivery.eventId()) {
            case "SKIPPED" -> Optional.empty();
            case "PREPARATION_FAILURE" -> throw new IllegalStateException("Preparation failed");
            default -> Optional.of(prepared(delivery));
        };
        DeliverPreparedNotificationUseCase deliverUseCase = delivery -> switch (delivery.eventId()) {
            case "STALE" -> false;
            case "DELIVERY_FAILURE" -> throw new IllegalStateException("Delivery failed");
            default -> true;
        };
        NotificationDeliveryBatchService service = new NotificationDeliveryBatchService(
                claimUseCase,
                prepareUseCase,
                deliverUseCase);

        NotificationDeliveryBatchResult result = service.process(COMMAND);

        assertThat(result).isEqualTo(new NotificationDeliveryBatchResult(5, 1, 1, 1, 2));
    }

    @Test
    void propagatesAClaimFailureBecauseNoBatchWasCreated() {
        ClaimNotificationDeliveriesUseCase claimUseCase = command -> {
            throw new IllegalStateException("Claim failed");
        };
        NotificationDeliveryBatchService service = new NotificationDeliveryBatchService(
                claimUseCase,
                delivery -> Optional.empty(),
                delivery -> false);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.process(COMMAND))
                .withMessage("Claim failed");
    }

    private ClaimedNotificationDelivery claimed(String eventId) {
        return new ClaimedNotificationDelivery(
                eventId,
                "CLIENT001",
                "credit_payment",
                "Test event " + eventId,
                1,
                "worker-1",
                NOW.plusSeconds(30),
                new NotificationDestination(
                        "SUB001",
                        URI.create("https://hooks.example.com/notifications")));
    }

    private PreparedNotificationDelivery prepared(ClaimedNotificationDelivery claimed) {
        UUID attemptId = UUID.randomUUID();
        return new PreparedNotificationDelivery(
                attemptId,
                claimed.eventId(),
                claimed.clientId(),
                claimed.eventType(),
                claimed.content(),
                claimed.destination(),
                claimed.deliveryCycle(),
                1,
                attemptId.toString(),
                NOW);
    }
}
