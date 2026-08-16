package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface ConfigureNotificationSubscriptionsUseCase {

    void configureAll(@NotEmpty List<@NotNull @Valid ConfigureNotificationSubscriptionCommand> commands);
}
