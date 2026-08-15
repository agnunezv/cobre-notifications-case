package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface NotificationSubscriptionConfigurationRepository {

    void save(@NotNull @Valid ConfigureNotificationSubscriptionCommand command);
}
