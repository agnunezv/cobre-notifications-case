package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.application.model.NotificationEventQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface ListNotificationEventsUseCase {

    @Valid NotificationEventPage list(@NotNull @Valid NotificationEventQuery query);
}
