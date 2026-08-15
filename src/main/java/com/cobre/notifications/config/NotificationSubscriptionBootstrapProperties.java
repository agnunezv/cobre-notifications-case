package com.cobre.notifications.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "notifications.subscription-bootstrap")
public record NotificationSubscriptionBootstrapProperties(
        boolean enabled,
        @Size(max = 64) String subscriptionId,
        @Size(max = 64) String clientId,
        URI endpointUrl,
        @NotNull List<String> eventTypes) {

    public NotificationSubscriptionBootstrapProperties {
        eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
    }

    @AssertTrue(
            message = "subscriptionId, clientId, endpointUrl, and eventTypes are required when "
                    + "subscription bootstrap is enabled")
    public boolean isConfigurationCompleteWhenEnabled() {
        return !enabled
                || StringUtils.hasText(subscriptionId)
                && StringUtils.hasText(clientId)
                && endpointUrl != null
                && !eventTypes.isEmpty()
                && eventTypes.stream().allMatch(StringUtils::hasText);
    }

    @AssertTrue(message = "endpointUrl must be an absolute HTTPS URL without user information or a fragment")
    public boolean isEndpointUrlValidWhenEnabled() {
        return !enabled
                || endpointUrl == null
                || "https".equalsIgnoreCase(endpointUrl.getScheme())
                && endpointUrl.getHost() != null
                && endpointUrl.getUserInfo() == null
                && endpointUrl.getFragment() == null;
    }
}
