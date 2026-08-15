package com.cobre.notifications.application.model;

import java.time.Duration;

public record ClaimNotificationDeliveriesCommand(
        String workerId,
        int batchSize,
        Duration leaseDuration) {

    public static final int MAX_BATCH_SIZE = 100;

    public ClaimNotificationDeliveriesCommand {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId is required");
        }
        if (workerId.length() > 128) {
            throw new IllegalArgumentException("workerId must not exceed 128 characters");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }
}
