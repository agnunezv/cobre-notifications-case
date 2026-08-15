package com.cobre.notifications.application.model;

public class NotificationEventNotFoundException extends RuntimeException {

    public NotificationEventNotFoundException() {
        super("Notification event was not found");
    }
}
