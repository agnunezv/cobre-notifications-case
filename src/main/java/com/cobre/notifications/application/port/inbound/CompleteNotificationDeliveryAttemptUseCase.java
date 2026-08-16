package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface CompleteNotificationDeliveryAttemptUseCase {

    boolean complete(
            @NotNull @Valid PreparedNotificationDelivery delivery, @NotNull @Valid WebhookDeliveryOutcome outcome);
}
