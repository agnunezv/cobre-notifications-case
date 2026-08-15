package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.application.model.NotificationEventQuery;

public interface NotificationEventQueryRepository {

    NotificationEventPage findPage(NotificationEventQuery query);
}
