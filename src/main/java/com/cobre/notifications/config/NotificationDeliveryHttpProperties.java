package com.cobre.notifications.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notifications.delivery.http")
public record NotificationDeliveryHttpProperties(
        @NotNull(message = "connectTimeout is required") Duration connectTimeout,
        @NotNull(message = "responseTimeout is required") Duration responseTimeout) {

    @AssertTrue(message = "connectTimeout must be positive") public boolean isConnectTimeoutPositive() {
        return connectTimeout == null || !connectTimeout.isZero() && !connectTimeout.isNegative();
    }

    @AssertTrue(message = "responseTimeout must be positive") public boolean isResponseTimeoutPositive() {
        return responseTimeout == null || !responseTimeout.isZero() && !responseTimeout.isNegative();
    }
}
