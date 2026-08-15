package com.cobre.notifications.domain.model;

public record RetryPolicy(int maximumAttempts) {

    public RetryPolicy {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be at least 1");
        }
    }

    public boolean hasAnotherAttemptAfter(int completedAttempts) {
        if (completedAttempts < 1) {
            throw new IllegalArgumentException("completedAttempts must be at least 1");
        }

        return completedAttempts < maximumAttempts;
    }
}
