package com.cobre.notifications.application.service;

import com.cobre.notifications.application.model.NotificationEventPage;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.port.inbound.ListNotificationEventsUseCase;
import com.cobre.notifications.application.port.outbound.NotificationEventQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationEventQueryService implements ListNotificationEventsUseCase {

    private final NotificationEventQueryRepository repository;

    public NotificationEventQueryService(NotificationEventQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationEventPage list(NotificationEventQuery query) {
        return repository.findPage(query);
    }
}
