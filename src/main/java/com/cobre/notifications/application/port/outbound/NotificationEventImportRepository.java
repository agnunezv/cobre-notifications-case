package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.domain.model.NotificationEvent;
import java.util.List;

public interface NotificationEventImportRepository {

    int insertIfAbsent(List<NotificationEvent> events);
}
