package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.domain.model.NotificationEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public interface ImportNotificationEventsUseCase {

    int importIfAbsent(@NotEmpty List<@Valid NotificationEvent> events);
}
