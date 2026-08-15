package com.cobre.notifications.application.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record NotificationEventPage(
        @NotNull List<@NotNull @Valid NotificationEventSummary> items,
        @PositiveOrZero int page,
        @Min(1) @Max(NotificationEventQuery.MAX_PAGE_SIZE) int size,
        boolean hasNext) {

    public NotificationEventPage {
        if (items != null) {
            items = List.copyOf(items);
        }
    }
}
