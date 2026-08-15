package com.cobre.notifications.domain.model;

public enum DeliveryStatus {
    PENDING,
    PROCESSING,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED;

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }
}
