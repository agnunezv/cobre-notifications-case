package com.cobre.notifications.application.service;

import com.cobre.notifications.application.port.inbound.ImportNotificationEventsUseCase;
import com.cobre.notifications.application.port.outbound.NotificationEventImportRepository;
import com.cobre.notifications.domain.model.NotificationEvent;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class NotificationEventImportService implements ImportNotificationEventsUseCase {

    private final NotificationEventImportRepository repository;

    public NotificationEventImportService(NotificationEventImportRepository repository) {
        this.repository = repository;
    }

    @Override
    public int importIfAbsent(List<NotificationEvent> events) {
        return repository.insertIfAbsent(events);
    }
}
