package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ReplayNotificationEventCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface ReplayNotificationEventUseCase {

    void replay(@NotNull @Valid ReplayNotificationEventCommand command);
}
