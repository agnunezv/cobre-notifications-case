package com.cobre.notifications.domain.model;

public class InvalidNotificationSubscriptionException extends RuntimeException {

    public InvalidNotificationSubscriptionException(String message) {
        super(message);
    }
}
