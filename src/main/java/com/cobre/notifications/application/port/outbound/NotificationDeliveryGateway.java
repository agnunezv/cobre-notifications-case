package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface NotificationDeliveryGateway {

    @NotNull @Valid WebhookDeliveryOutcome deliver(@NotNull @Valid PreparedNotificationDelivery delivery);
}
