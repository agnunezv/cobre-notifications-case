package com.cobre.notifications.application.model;

public class InvalidNotificationEventQueryException extends RuntimeException {

    public InvalidNotificationEventQueryException(String message) {
        super(message);
    }
}
