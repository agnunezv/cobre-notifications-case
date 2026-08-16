package com.cobre.notifications.config;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notifications.delivery.worker")
public record NotificationDeliveryWorkerProperties(
        boolean enabled,

        @NotBlank(message = "workerId is required") @Size(max = 128, message = "workerId must not exceed 128 characters") String workerId,

        @Min(value = 1, message = "batchSize must be between 1 and 100") @Max(value = ClaimNotificationDeliveriesCommand.MAX_BATCH_SIZE, message = "batchSize must be between 1 and 100") int batchSize,

        @NotNull(message = "pollInterval is required") Duration pollInterval,
        @NotNull(message = "initialDelay is required") Duration initialDelay,
        @NotNull(message = "leaseDuration is required") Duration leaseDuration) {

    @AssertTrue(message = "pollInterval must be positive") public boolean isPollIntervalPositive() {
        return pollInterval == null || !pollInterval.isZero() && !pollInterval.isNegative();
    }

    @AssertTrue(message = "initialDelay must not be negative") public boolean isInitialDelayNotNegative() {
        return initialDelay == null || !initialDelay.isNegative();
    }

    @AssertTrue(message = "leaseDuration must be positive") public boolean isLeaseDurationPositive() {
        return leaseDuration == null || !leaseDuration.isZero() && !leaseDuration.isNegative();
    }

    public ClaimNotificationDeliveriesCommand claimCommand() {
        return new ClaimNotificationDeliveriesCommand(workerId, batchSize, leaseDuration);
    }
}
