package com.cobre.notifications.domain.model;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RetryPolicy(int maximumAttempts, List<Duration> retryDelays) {

    public RetryPolicy {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be at least 1");
        }
        if (retryDelays == null) {
            throw new IllegalArgumentException("retryDelays must not be null");
        }
        if (retryDelays.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("retryDelays must not contain null entries");
        }

        retryDelays = List.copyOf(retryDelays);
        if (retryDelays.size() != maximumAttempts - 1) {
            throw new IllegalArgumentException("retryDelays must contain one delay for every automatic retry");
        }
        if (retryDelays.stream().anyMatch(delay -> delay.isZero() || delay.isNegative())) {
            throw new IllegalArgumentException("retryDelays must contain only positive durations");
        }
    }

    public boolean hasAnotherAttemptAfter(int completedAttempts) {
        if (completedAttempts < 1) {
            throw new IllegalArgumentException("completedAttempts must be at least 1");
        }

        return completedAttempts < maximumAttempts;
    }

    public Optional<Duration> retryDelayAfter(int completedAttempts) {
        if (!hasAnotherAttemptAfter(completedAttempts)) {
            return Optional.empty();
        }

        return Optional.of(retryDelays.get(completedAttempts - 1));
    }
}
