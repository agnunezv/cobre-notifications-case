package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface ClaimNotificationDeliveriesUseCase {

    List<@Valid ClaimedNotificationDelivery> claimDue(
            @NotNull @Valid ClaimNotificationDeliveriesCommand command);
}
