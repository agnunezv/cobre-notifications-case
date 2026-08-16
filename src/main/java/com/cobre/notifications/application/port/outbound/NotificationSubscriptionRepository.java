package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.domain.model.NotificationSubscription;
import java.util.List;

public interface NotificationSubscriptionRepository {

    List<NotificationSubscription> findActiveMatches(NotificationSubscriptionQuery query);
}
