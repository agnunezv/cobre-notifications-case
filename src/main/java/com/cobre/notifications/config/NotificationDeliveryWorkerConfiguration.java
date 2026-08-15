package com.cobre.notifications.config;

import com.cobre.notifications.adapter.in.scheduling.ScheduledNotificationDeliveryWorker;
import com.cobre.notifications.application.port.inbound.ProcessNotificationDeliveryBatchUseCase;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(NotificationDeliveryWorkerProperties.class)
public class NotificationDeliveryWorkerConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "notifications.delivery.worker",
            name = "enabled",
            havingValue = "true")
    ScheduledNotificationDeliveryWorker scheduledNotificationDeliveryWorker(
            ProcessNotificationDeliveryBatchUseCase processBatch,
            NotificationDeliveryWorkerProperties properties,
            NotificationDeliveryMetrics metrics) {
        return new ScheduledNotificationDeliveryWorker(processBatch, properties, metrics);
    }
}
