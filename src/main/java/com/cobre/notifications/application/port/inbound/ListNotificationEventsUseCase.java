package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.application.model.NotificationEventQuery;

public interface ListNotificationEventsUseCase {

    NotificationEventPage list(NotificationEventQuery query);
}
