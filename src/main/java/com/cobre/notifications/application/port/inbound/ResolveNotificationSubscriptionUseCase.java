package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.NotificationSubscriptionQuery;
import com.cobre.notifications.domain.model.NotificationSubscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Optional;

public interface ResolveNotificationSubscriptionUseCase {

    Optional<@Valid NotificationSubscription> resolve(
            @NotNull @Valid NotificationSubscriptionQuery query);
}
