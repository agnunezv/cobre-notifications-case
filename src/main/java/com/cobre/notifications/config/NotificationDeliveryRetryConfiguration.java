package com.cobre.notifications.config;

import com.cobre.notifications.domain.model.RetryPolicy;
import com.cobre.notifications.domain.service.DeliveryLifecycle;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationDeliveryRetryProperties.class)
public class NotificationDeliveryRetryConfiguration {

    @Bean
    RetryPolicy notificationRetryPolicy(NotificationDeliveryRetryProperties properties) {
        return new RetryPolicy(properties.maximumAttempts(), properties.delays());
    }

    @Bean
    DeliveryLifecycle deliveryLifecycle(RetryPolicy notificationRetryPolicy) {
        return new DeliveryLifecycle(notificationRetryPolicy);
    }
}
