package com.cobre.notifications.adapter.in.bootstrap;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionsUseCase;
import com.cobre.notifications.config.NotificationSubscriptionBootstrapProperties;
import com.cobre.notifications.domain.model.NotificationSubscription;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class NotificationSubscriptionBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSubscriptionBootstrap.class);

    private final ConfigureNotificationSubscriptionsUseCase configureSubscriptions;
    private final NotificationSubscriptionBootstrapProperties properties;
    private final Clock clock;

    public NotificationSubscriptionBootstrap(
            ConfigureNotificationSubscriptionsUseCase configureSubscriptions,
            NotificationSubscriptionBootstrapProperties properties,
            Clock clock) {
        this.configureSubscriptions = configureSubscriptions;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var configuredAt = clock.instant();
        List<ConfigureNotificationSubscriptionCommand> commands = properties.subscriptions().stream()
                .map(subscription -> new ConfigureNotificationSubscriptionCommand(
                        new NotificationSubscription(
                                subscription.subscriptionId(), subscription.clientId(), subscription.endpointUrl()),
                        new LinkedHashSet<>(subscription.eventTypes()),
                        configuredAt))
                .toList();

        configureSubscriptions.configureAll(commands);

        LOGGER.info(
                "Configured {} notification subscriptions for {} clients",
                commands.size(),
                commands.stream()
                        .map(command -> command.subscription().clientId())
                        .distinct()
                        .count());
    }
}
