package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ClaimedNotificationDelivery;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

public interface PrepareNotificationDeliveryUseCase {

    Optional<@Valid PreparedNotificationDelivery> prepare(@NotNull @Valid ClaimedNotificationDelivery claimedDelivery);
}
