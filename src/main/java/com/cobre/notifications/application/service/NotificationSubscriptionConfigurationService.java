package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.ConfigureNotificationSubscriptionCommand;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionUseCase;
import com.cobre.notifications.application.port.inbound.ConfigureNotificationSubscriptionsUseCase;
import com.cobre.notifications.application.port.outbound.NotificationSubscriptionConfigurationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationSubscriptionConfigurationService
        implements ConfigureNotificationSubscriptionUseCase, ConfigureNotificationSubscriptionsUseCase {

    private final NotificationSubscriptionConfigurationRepository repository;

    public NotificationSubscriptionConfigurationService(NotificationSubscriptionConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void configure(ConfigureNotificationSubscriptionCommand command) {
        repository.save(command);
    }

    @Override
    @Transactional
    public void configureAll(List<ConfigureNotificationSubscriptionCommand> commands) {
        commands.forEach(repository::save);
    }
}
