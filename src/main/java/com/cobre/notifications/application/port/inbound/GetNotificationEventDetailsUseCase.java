package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.NotificationEventDetails;
import com.cobre.notifications.application.model.NotificationEventDetailsQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface GetNotificationEventDetailsUseCase {

    @NotNull @Valid NotificationEventDetails get(@NotNull @Valid NotificationEventDetailsQuery query);
}
