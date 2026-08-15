package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;

import java.util.List;

public interface ClaimNotificationDeliveriesUseCase {

    List<ClaimedNotificationDelivery> claimDue(ClaimNotificationDeliveriesCommand command);
}
