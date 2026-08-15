package com.cobre.notifications.domain.model;

public class InvalidNotificationDestinationException extends RuntimeException {

    public InvalidNotificationDestinationException(String message) {
        super(message);
    }
}
