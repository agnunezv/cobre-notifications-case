package com.cobre.notifications.domain.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;

public record NotificationDestination(
        @NotBlank @Size(max = 64) String subscriptionId,
        @NotNull URI endpointUrl) {

    public static NotificationDestination from(NotificationSubscription subscription) {
        return new NotificationDestination(subscription.subscriptionId(), subscription.endpointUrl());
    }

    public static NotificationDestination fromStoredValues(String subscriptionId, String endpointUrl) {
        try {
            return new NotificationDestination(subscriptionId, URI.create(endpointUrl));
        } catch (IllegalArgumentException exception) {
            throw new InvalidNotificationDestinationException(
                    "Notification destination for subscription %s is invalid".formatted(subscriptionId));
        }
    }

    @AssertTrue(message = "endpointUrl must be an absolute HTTPS URL without user information or a fragment") public boolean isEndpointUrlValid() {
        return endpointUrl == null
                || "https".equalsIgnoreCase(endpointUrl.getScheme())
                        && endpointUrl.getHost() != null
                        && endpointUrl.getUserInfo() == null
                        && endpointUrl.getFragment() == null;
    }
}
