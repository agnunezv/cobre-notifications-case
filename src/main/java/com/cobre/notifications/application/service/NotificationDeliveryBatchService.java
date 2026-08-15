package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.port.inbound.ClaimNotificationDeliveriesUseCase;
import com.cobre.notifications.application.port.inbound.DeliverPreparedNotificationUseCase;
import com.cobre.notifications.application.port.inbound.PrepareNotificationDeliveryUseCase;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@Validated
public class NotificationDeliveryBatchService implements ProcessNotificationDeliveryBatchUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryBatchService.class);

    private final ClaimNotificationDeliveriesUseCase claimDeliveries;
    private final PrepareNotificationDeliveryUseCase prepareDelivery;
    private final DeliverPreparedNotificationUseCase deliverPreparedNotification;

    public NotificationDeliveryBatchService(
            ClaimNotificationDeliveriesUseCase claimDeliveries,
            PrepareNotificationDeliveryUseCase prepareDelivery,
            DeliverPreparedNotificationUseCase deliverPreparedNotification) {
        this.claimDeliveries = claimDeliveries;
        this.prepareDelivery = prepareDelivery;
        this.deliverPreparedNotification = deliverPreparedNotification;
    }

    @Override
    public NotificationDeliveryBatchResult process(ClaimNotificationDeliveriesCommand command) {
        List<ClaimedNotificationDelivery> claimedDeliveries = claimDeliveries.claimDue(command);
        int preparationSkippedCount = 0;
        int completionAppliedCount = 0;
        int staleCompletionCount = 0;
        int processingFailureCount = 0;

        for (ClaimedNotificationDelivery claimedDelivery : claimedDeliveries) {
            try {
                Optional<PreparedNotificationDelivery> prepared = prepareDelivery.prepare(claimedDelivery);
                if (prepared.isEmpty()) {
                    preparationSkippedCount++;
                } else if (deliverPreparedNotification.deliver(prepared.orElseThrow())) {
                    completionAppliedCount++;
                } else {
                    staleCompletionCount++;
                }
            } catch (RuntimeException exception) {
                processingFailureCount++;
                LOGGER.error(
                        "Unexpected failure while processing notification event {} claimed by {}",
                        claimedDelivery.eventId(),
                        command.workerId(),
                        exception);
            }
        }

        return new NotificationDeliveryBatchResult(
                claimedDeliveries.size(),
                preparationSkippedCount,
                completionAppliedCount,
                staleCompletionCount,
                processingFailureCount);
    }
}
