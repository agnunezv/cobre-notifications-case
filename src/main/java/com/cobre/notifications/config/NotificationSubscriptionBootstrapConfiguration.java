package com.cobre.notifications.config;

import com.cobre.notifications.adapter.in.bootstrap.NotificationSubscriptionBootstrap;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationSubscriptionBootstrapProperties.class)
public class NotificationSubscriptionBootstrapConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "notifications.subscription-bootstrap",
            name = "enabled",
            havingValue = "true")
    NotificationSubscriptionBootstrap notificationSubscriptionBootstrap(
            ConfigureNotificationSubscriptionUseCase configureSubscription,
            NotificationSubscriptionBootstrapProperties properties,
            Clock clock) {
        return new NotificationSubscriptionBootstrap(configureSubscription, properties, clock);
    }
}
