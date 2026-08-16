package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.NotificationEventDetails;
import com.cobre.notifications.application.model.NotificationEventDetailsQuery;
import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.application.model.NotificationEventQuery;
import java.util.Optional;

public interface NotificationEventQueryRepository {

    NotificationEventPage findPage(NotificationEventQuery query);

    Optional<NotificationEventDetails> findDetails(NotificationEventDetailsQuery query);
}
