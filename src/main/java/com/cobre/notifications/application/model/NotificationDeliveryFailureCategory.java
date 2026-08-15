package com.cobre.notifications.application.model;

public enum NotificationDeliveryFailureCategory {
    HTTP_RESPONSE,
    TIMEOUT,
    CONNECTION_ERROR,
    TLS_ERROR,
    HTTP_CLIENT_ERROR
}
