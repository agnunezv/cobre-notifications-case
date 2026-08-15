package com.cobre.notifications.domain.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.URI;

public record NotificationSubscription(
        @NotBlank @Size(max = 64) String subscriptionId,
        @NotBlank @Size(max = 64) String clientId,
        @NotNull URI endpointUrl) {

    public static NotificationSubscription fromStoredValues(
            String subscriptionId,
            String clientId,
            String endpointUrl) {
        try {
            return new NotificationSubscription(subscriptionId, clientId, URI.create(endpointUrl));
        } catch (IllegalArgumentException exception) {
            throw new InvalidNotificationSubscriptionException(
                    "Subscription %s has an invalid endpoint URL".formatted(subscriptionId));
        }
    }

    @AssertTrue(message = "endpointUrl must be an absolute HTTPS URL without user information or a fragment")
    public boolean isEndpointUrlValid() {
        return endpointUrl == null
                || "https".equalsIgnoreCase(endpointUrl.getScheme())
                && endpointUrl.getHost() != null
                && endpointUrl.getUserInfo() == null
                && endpointUrl.getFragment() == null;
    }
}
