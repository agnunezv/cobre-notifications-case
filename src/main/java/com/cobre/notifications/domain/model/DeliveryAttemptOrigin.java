package com.cobre.notifications.domain.model;

public enum DeliveryAttemptOrigin {
    INITIAL,
    AUTOMATIC_RETRY,
    MANUAL_REPLAY,
    LEASE_RECOVERY
}
