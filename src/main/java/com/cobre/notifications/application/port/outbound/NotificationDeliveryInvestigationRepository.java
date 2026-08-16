package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.NotificationDeliveryInvestigation;
import com.cobre.notifications.application.model.NotificationDeliveryInvestigationQuery;
import java.util.Optional;

public interface NotificationDeliveryInvestigationRepository {

    Optional<NotificationDeliveryInvestigation> find(NotificationDeliveryInvestigationQuery query);
}
