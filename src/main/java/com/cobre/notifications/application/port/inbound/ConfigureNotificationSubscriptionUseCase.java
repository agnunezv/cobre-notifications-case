package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface ConfigureNotificationSubscriptionUseCase {

    void configure(@NotNull @Valid ConfigureNotificationSubscriptionCommand command);
}
