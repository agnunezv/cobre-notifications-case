package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.NotificationDeliveryInvestigation;
import com.cobre.notifications.application.model.NotificationDeliveryInvestigationQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface InvestigateNotificationDeliveryUseCase {

    @NotNull @Valid NotificationDeliveryInvestigation investigate(@NotNull @Valid NotificationDeliveryInvestigationQuery query);
}
