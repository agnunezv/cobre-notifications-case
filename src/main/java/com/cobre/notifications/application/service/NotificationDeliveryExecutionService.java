package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.inbound.DeliverPreparedNotificationUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryGateway;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationDeliveryExecutionService implements DeliverPreparedNotificationUseCase {

    private final NotificationDeliveryGateway deliveryGateway;
    private final CompleteNotificationDeliveryAttemptUseCase completeAttempt;

    public NotificationDeliveryExecutionService(
            NotificationDeliveryGateway deliveryGateway,
            CompleteNotificationDeliveryAttemptUseCase completeAttempt) {
        this.deliveryGateway = deliveryGateway;
        this.completeAttempt = completeAttempt;
    }

    @Override
    public boolean deliver(PreparedNotificationDelivery delivery) {
        WebhookDeliveryOutcome outcome = deliveryGateway.deliver(delivery);
        return completeAttempt.complete(delivery, outcome);
    }
}
