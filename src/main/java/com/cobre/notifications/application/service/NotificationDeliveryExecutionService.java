package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.inbound.CompleteNotificationDeliveryAttemptUseCase;
import com.cobre.notifications.application.port.inbound.DeliverPreparedNotificationUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryGateway;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationDeliveryExecutionService implements DeliverPreparedNotificationUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryExecutionService.class);

    private final NotificationDeliveryGateway deliveryGateway;
    private final CompleteNotificationDeliveryAttemptUseCase completeAttempt;
    private final NotificationDeliveryMetrics metrics;

    public NotificationDeliveryExecutionService(
            NotificationDeliveryGateway deliveryGateway,
            CompleteNotificationDeliveryAttemptUseCase completeAttempt,
            NotificationDeliveryMetrics metrics) {
        this.deliveryGateway = deliveryGateway;
        this.completeAttempt = completeAttempt;
        this.metrics = metrics;
    }

    @Override
    public boolean deliver(PreparedNotificationDelivery delivery) {
        WebhookDeliveryOutcome outcome = deliveryGateway.deliver(delivery);
        recordAttempt(delivery, outcome);
        return completeAttempt.complete(delivery, outcome);
    }

    private void recordAttempt(PreparedNotificationDelivery delivery, WebhookDeliveryOutcome outcome) {
        try {
            metrics.recordAttempt(delivery, outcome);
        } catch (RuntimeException exception) {
            LOGGER.warn("Notification delivery attempt metrics could not be recorded", exception);
        }
    }
}
