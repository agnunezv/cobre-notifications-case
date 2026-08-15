package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface DeliverPreparedNotificationUseCase {

    boolean deliver(@NotNull @Valid PreparedNotificationDelivery delivery);
}
