package com.cobre.notifications.adapter.in.bootstrap;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionUseCase;
import com.cobre.notifications.config.NotificationSubscriptionBootstrapProperties;
import com.cobre.notifications.domain.model.NotificationSubscription;
import java.time.Clock;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class NotificationSubscriptionBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSubscriptionBootstrap.class);

    private final ConfigureNotificationSubscriptionUseCase configureSubscription;
    private final NotificationSubscriptionBootstrapProperties properties;
    private final Clock clock;

    public NotificationSubscriptionBootstrap(
            ConfigureNotificationSubscriptionUseCase configureSubscription,
            NotificationSubscriptionBootstrapProperties properties,
            Clock clock) {
        this.configureSubscription = configureSubscription;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        var eventTypes = new LinkedHashSet<>(properties.eventTypes());
        configureSubscription.configure(new ConfigureNotificationSubscriptionCommand(
                new NotificationSubscription(
                        properties.subscriptionId(), properties.clientId(), properties.endpointUrl()),
                eventTypes,
                clock.instant()));

        LOGGER.info(
                "Notification subscription {} configured for client {} with {} event types",
                properties.subscriptionId(),
                properties.clientId(),
                eventTypes.size());
    }
}
