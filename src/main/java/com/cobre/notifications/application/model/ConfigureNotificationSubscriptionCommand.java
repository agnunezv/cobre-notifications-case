package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.NotificationSubscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record ConfigureNotificationSubscriptionCommand(
        @NotNull @Valid NotificationSubscription subscription,
        @NotEmpty Set<@NotBlank @Size(max = 128) String> eventTypes,
        @NotNull Instant configuredAt) {

    public ConfigureNotificationSubscriptionCommand {
        eventTypes = eventTypes == null ? Set.of() : Set.copyOf(eventTypes);
    }
}
