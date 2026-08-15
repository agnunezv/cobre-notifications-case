package com.cobre.notifications.domain.service;

import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.RetryPolicy;

import java.util.Objects;

public final class DeliveryLifecycle {

    private final RetryPolicy retryPolicy;

    public DeliveryLifecycle(RetryPolicy retryPolicy) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
    }

    public DeliveryStatus claim(DeliveryStatus currentStatus) {
        Objects.requireNonNull(currentStatus, "currentStatus must not be null");

        if (currentStatus == DeliveryStatus.PENDING
                || currentStatus == DeliveryStatus.RETRY_SCHEDULED) {
            return DeliveryStatus.PROCESSING;
        }

        throw invalidTransition("claim", currentStatus);
    }

    public DeliveryStatus finishAttempt(
            DeliveryStatus currentStatus,
            DeliveryAttemptResult result,
            int completedAttempts) {
        Objects.requireNonNull(currentStatus, "currentStatus must not be null");
        Objects.requireNonNull(result, "result must not be null");

        if (currentStatus != DeliveryStatus.PROCESSING) {
            throw invalidTransition("finish an attempt for", currentStatus);
        }
        if (completedAttempts < 1) {
            throw new IllegalArgumentException("completedAttempts must be at least 1");
        }

        return switch (result) {
            case SUCCESS -> DeliveryStatus.COMPLETED;
            case PERMANENT_FAILURE -> DeliveryStatus.FAILED;
            case RETRYABLE_FAILURE -> retryPolicy.hasAnotherAttemptAfter(completedAttempts)
                    ? DeliveryStatus.RETRY_SCHEDULED
                    : DeliveryStatus.FAILED;
        };
    }

    public DeliveryStatus replay(DeliveryStatus currentStatus) {
        Objects.requireNonNull(currentStatus, "currentStatus must not be null");

        if (currentStatus == DeliveryStatus.FAILED) {
            return DeliveryStatus.PENDING;
        }

        throw invalidTransition("replay", currentStatus);
    }

    private static IllegalStateException invalidTransition(String action, DeliveryStatus currentStatus) {
        return new IllegalStateException("Cannot " + action + " delivery in " + currentStatus + " status");
    }
}
