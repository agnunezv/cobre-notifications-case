package com.cobre.notifications.application.model;

public class NotificationEventReplayNotAllowedException extends RuntimeException {

    public NotificationEventReplayNotAllowedException(Throwable cause) {
        super("Only failed notification events can be replayed", cause);
    }
}
