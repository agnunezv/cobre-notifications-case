package com.cobre.notifications.config;

import com.cobre.notifications.adapter.in.bootstrap.NotificationSubscriptionBootstrap;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionsUseCase;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationSubscriptionBootstrapProperties.class)
public class NotificationSubscriptionBootstrapConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "notifications.subscription-bootstrap", name = "enabled", havingValue = "true")
    NotificationSubscriptionBootstrap notificationSubscriptionBootstrap(
            ConfigureNotificationSubscriptionsUseCase configureSubscriptions,
            NotificationSubscriptionBootstrapProperties properties,
            Clock clock) {
        return new NotificationSubscriptionBootstrap(configureSubscriptions, properties, clock);
    }
}
