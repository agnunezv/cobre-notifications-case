package com.cobre.notifications.application.model;

import java.util.List;

public record NotificationEventPage(
        List<NotificationEventSummary> items,
        int page,
        int size,
        boolean hasNext) {

    public NotificationEventPage {
        items = List.copyOf(items);
    }
}
