package com.cobre.notifications.adapter.in.bootstrap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record NotificationEventsJsonFile(@NotEmpty @Valid List<NotificationEventJsonRecord> events) {

    public NotificationEventsJsonFile {
        if (events != null) {
            events = List.copyOf(events);
        }
    }
}
