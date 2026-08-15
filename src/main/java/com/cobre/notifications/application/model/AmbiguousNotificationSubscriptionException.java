package com.cobre.notifications.application.model;

public class AmbiguousNotificationSubscriptionException extends RuntimeException {

    public AmbiguousNotificationSubscriptionException(NotificationSubscriptionQuery query) {
        super("Multiple active subscriptions match client %s and event type %s"
                .formatted(query.clientId(), query.eventType()));
    }
}
