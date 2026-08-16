package com.cobre.notifications.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notifications.subscription-bootstrap")
public record NotificationSubscriptionBootstrapProperties(
        boolean enabled, @NotNull List<@NotNull @Valid SubscriptionProperties> subscriptions) {

    public NotificationSubscriptionBootstrapProperties {
        subscriptions = subscriptions == null ? List.of() : List.copyOf(subscriptions);
    }

    @AssertTrue(message = "at least one complete subscription is required when " + "subscription bootstrap is enabled") public boolean isConfigurationCompleteWhenEnabled() {
        return !enabled
                || !subscriptions.isEmpty()
                        && subscriptions.stream()
                                .allMatch(subscription -> subscription != null && subscription.isComplete());
    }

    @AssertTrue(message = "subscription IDs must be unique") public boolean hasUniqueSubscriptionIdsWhenEnabled() {
        if (!enabled) {
            return true;
        }
        Set<String> subscriptionIds = new HashSet<>();
        return subscriptions.stream()
                .filter(subscription -> subscription != null && StringUtils.hasText(subscription.subscriptionId()))
                .map(SubscriptionProperties::subscriptionId)
                .allMatch(subscriptionIds::add);
    }

    @AssertTrue(message = "each client and event type route must belong to only one configured subscription") public boolean hasUniqueRoutesWhenEnabled() {
        if (!enabled) {
            return true;
        }
        Set<ClientEventTypeRoute> routes = new HashSet<>();
        return subscriptions.stream()
                .filter(subscription -> subscription != null && StringUtils.hasText(subscription.clientId()))
                .flatMap(subscription -> subscription.eventTypes().stream()
                        .filter(StringUtils::hasText)
                        .map(eventType -> new ClientEventTypeRoute(subscription.clientId(), eventType)))
                .allMatch(routes::add);
    }

    public record SubscriptionProperties(
            @Size(max = 64) String subscriptionId,
            @Size(max = 64) String clientId,
            URI endpointUrl,
            @NotNull List<@NotNull @Size(max = 128) String> eventTypes) {

        public SubscriptionProperties {
            eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
        }

        boolean isComplete() {
            return StringUtils.hasText(subscriptionId)
                    && StringUtils.hasText(clientId)
                    && endpointUrl != null
                    && !eventTypes.isEmpty()
                    && eventTypes.stream().allMatch(StringUtils::hasText);
        }

        @AssertTrue(message = "endpointUrl must be an absolute HTTPS URL without user information or a fragment") public boolean isEndpointUrlValid() {
            return endpointUrl == null
                    || "https".equalsIgnoreCase(endpointUrl.getScheme())
                            && endpointUrl.getHost() != null
                            && endpointUrl.getUserInfo() == null
                            && endpointUrl.getFragment() == null;
        }
    }

    private record ClientEventTypeRoute(String clientId, String eventType) {}
}
