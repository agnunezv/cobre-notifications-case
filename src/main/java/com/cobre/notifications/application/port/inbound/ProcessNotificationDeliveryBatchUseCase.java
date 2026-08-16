package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface ProcessNotificationDeliveryBatchUseCase {

    @NotNull @Valid NotificationDeliveryBatchResult process(@NotNull @Valid ClaimNotificationDeliveriesCommand command);
}
