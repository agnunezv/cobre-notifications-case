package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryClaimRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationDeliveryClaimService implements ClaimNotificationDeliveriesUseCase {

    private final NotificationDeliveryClaimRepository repository;
    private final Clock clock;

    public NotificationDeliveryClaimService(NotificationDeliveryClaimRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<ClaimedNotificationDelivery> claimDue(ClaimNotificationDeliveriesCommand command) {
        Instant claimedAt = clock.instant();
        Instant leaseUntil = claimedAt.plus(command.leaseDuration());
        return repository.claimDue(command.workerId(), claimedAt, leaseUntil, command.batchSize());
    }
}
