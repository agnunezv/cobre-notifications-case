package com.cobre.notifications.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notifications.delivery.retry")
public record NotificationDeliveryRetryProperties(
        @Min(value = 1, message = "maximumAttempts must be between 1 and 10") @Max(value = MAXIMUM_ATTEMPTS, message = "maximumAttempts must be between 1 and 10") int maximumAttempts,

        @NotNull List<@NotNull Duration> delays) {

    public static final int MAXIMUM_ATTEMPTS = 10;

    public NotificationDeliveryRetryProperties {
        delays = delays == null ? List.of() : List.copyOf(delays);
    }

    @AssertTrue(message = "delays must contain one entry for every automatic retry") public boolean isDelayCountConsistent() {
        return maximumAttempts < 1 || delays.size() == maximumAttempts - 1;
    }

    @AssertTrue(message = "delays must contain only positive durations") public boolean isEveryDelayPositive() {
        return delays.stream().allMatch(delay -> delay == null || !delay.isZero() && !delay.isNegative());
    }
}
